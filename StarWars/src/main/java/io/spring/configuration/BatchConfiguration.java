package io.spring.configuration;

import java.io.File;
import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.step.tasklet.SystemCommandTasklet;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.builder.MultiResourceItemReaderBuilder;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.RegexLineTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceArrayPropertyEditor;

@Configuration
public class BatchConfiguration {

	private static final String WORKING_DIRECTORY = "/Users/mminella/Documents/SpringSourceWorkspace/StarWars/target/";
	private static final String MAX_MIND_DB = "/usr/local/share/GeoIP/GeoLite2-Country.mmdb";
	private static final String REPORT_OUTPUT_DIR = "/Users/mminella/Documents/SpringSourceWorkspace/StarWars/target/report/";

	@Autowired
	private JobBuilderFactory jobBuilderFactory;

	@Autowired
	private StepBuilderFactory stepBuilderFactory;

	// Step 1 Beans
	@Bean
	@StepScope
	public SystemCommandTasklet fileSplittingTasklet(@Value("#{jobParameters['inputFile']}")String inputFile,
			@Value("#{jobParameters['stagingDirectory']}") String stagingDirectory) throws Exception {

		File file = new File(stagingDirectory);
		if(!file.exists()) {
			file.mkdir();
		}

		SystemCommandTasklet tasklet = new SystemCommandTasklet();

		tasklet.setCommand(String.format("split -a 5 -l 10000 %s %s", inputFile, stagingDirectory));
		tasklet.setTimeout(60000l);
		tasklet.setWorkingDirectory(WORKING_DIRECTORY);
		tasklet.afterPropertiesSet();

		return tasklet;
	}

	@Bean
	public Step step1(SystemCommandTasklet fileSplittingTasklet) throws Exception {
		return stepBuilderFactory.get("step1")
				.tasklet(fileSplittingTasklet)
				.listener(fileSplittingTasklet)
				.build();
	}

	// Step 2 beans

	@Bean
	@StepScope
	public MultiResourceItemReader<LogEntry> logEntryItemReader(@Value("#{jobParameters['stagingDirectory']}") String stagingDirectory) throws Exception {
		Resource[] resources = getResources(stagingDirectory);

		RegexLineTokenizer regexLineTokenizer = new RegexLineTokenizer();
		regexLineTokenizer.setRegex("(\\d+\\.\\d+\\.\\d+\\.\\d+) - (-|doink|admin) \\[(\\d+/\\w+/\\d+:\\d+:\\d+:\\d+ [+-]\\d+)\\] \"\\w+ ((/[^/ ]*)+|http://www.waxy.org(/\\w+-?\\w+\\.?\\w+)*)");

		DefaultLineMapper<LogEntry> defaultLineMapper = new DefaultLineMapper<>();
		defaultLineMapper.setLineTokenizer(regexLineTokenizer);
		defaultLineMapper.setFieldSetMapper(new LogEntryFieldSetMapper());
		defaultLineMapper.afterPropertiesSet();

		FlatFileItemReader<LogEntry> delegate = new FlatFileItemReader<>();
		delegate.setLineMapper(defaultLineMapper);
		delegate.afterPropertiesSet();

		return new MultiResourceItemReaderBuilder<LogEntry>()
				.name("logEntryItemReader")
				.delegate(delegate)
				.resources(resources)
				.build();
	}


	private Resource[] getResources(String stagingDirectory) {
		ResourceArrayPropertyEditor resourceLoader = new ResourceArrayPropertyEditor();
		resourceLoader.setAsText("file:" + stagingDirectory + "/*");
		return  (Resource[]) resourceLoader.getValue();
	}

	@Bean
	public GeocodingItemProcessor geocodingItemProcessor() throws Exception {
		return new GeocodingItemProcessor(MAX_MIND_DB);
	}

	@Bean
	public JdbcBatchItemWriter<LogEntry> jdbcWriter(DataSource dataSource) {
		return new JdbcBatchItemWriterBuilder<LogEntry>()
				.beanMapped()
				.sql("INSERT INTO logEntry (ip_address, requested_url, country_code, view_date) VALUES(:ipAddress, :requestedUrl, :countryCode, :viewDate)")
				.dataSource(dataSource)
				.build();
	}

	@Bean
	@SuppressWarnings("unchecked")
	public Step step2(ItemStream logEntryItemReader, ItemProcessor<LogEntry, LogEntry> geocodingItemProcessor, ItemWriter<LogEntry> jdbcWriter) throws Exception {
		return stepBuilderFactory.get("step2")
				.<LogEntry, LogEntry> chunk(10000)
				.reader((ItemReader<LogEntry>) logEntryItemReader)
				.processor(geocodingItemProcessor)
				.writer(jdbcWriter)
				.faultTolerant().skip(Exception.class).skipLimit(2000)
				.build();
	}

	// Partitioning Beans
	//
	//		@Bean
	//		@StepScope
	//		public PartitionHandler partitionHandler(Step step2) throws Exception {
	//			TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
	//
	//			partitionHandler.setGridSize(4);
	//			partitionHandler.setTaskExecutor(new SimpleAsyncTaskExecutor());
	//			partitionHandler.setStep(step2);
	//
	//			return partitionHandler;
	//		}
	//
	//		@Bean
	//		@StepScope
	//		public MultiResourcePartitioner partitioner(@Value("#{jobParameters['stagingDirectory']}") String stagingDirectory) {
	//			MultiResourcePartitioner partitioner = new MultiResourcePartitioner();
	//
	//			partitioner.setResources(getResources(stagingDirectory));
	//
	//			return partitioner;
	//		}
	//
	//
	//		@Bean
	//		@StepScope
	//		public FlatFileItemReader<LogEntry> logEntryItemReader(@Value("#{stepExecutionContext['fileName']}") Resource file) throws Exception {
	//
	//			RegexLineTokenizer regexLineTokenizer = new RegexLineTokenizer();
	//			regexLineTokenizer.setRegex("(\\d+\\.\\d+\\.\\d+\\.\\d+) - (-|doink|admin) \\[(\\d+/\\w+/\\d+:\\d+:\\d+:\\d+ [+-]\\d+)\\] \"\\w+ ((/[^/ ]*)+|http://www.waxy.org(/\\w+-?\\w+\\.?\\w+)*)");
	//
	//			DefaultLineMapper<LogEntry> defaultLineMapper = new DefaultLineMapper<>();
	//			defaultLineMapper.setLineTokenizer(regexLineTokenizer);
	//			defaultLineMapper.setFieldSetMapper(new LogEntryFieldSetMapper());
	//			defaultLineMapper.afterPropertiesSet();
	//
	//			FlatFileItemReader<LogEntry> delegate = new FlatFileItemReader<LogEntry>();
	//			delegate.setLineMapper(defaultLineMapper);
	//			delegate.setResource(file);
	//			delegate.afterPropertiesSet();
	//
	//			return delegate;
	//		}
	//
	//		@Bean
	//		public Step partitionedStep2(PartitionHandler partitionHandler,
	//				Partitioner partitioner) {
	//			return stepBuilderFactory.get("partitionedStep2")
	//					.partitioner("step2", partitioner)
	//					.partitionHandler(partitionHandler)
	//					.build();
	//		}

	// Step 3 beans

	@Bean
	public ReportTasklet reportTasklet() {
		return new ReportTasklet(REPORT_OUTPUT_DIR);
	}

	@Bean
	public Step step3() {
		return stepBuilderFactory.get("step3")
				.tasklet(reportTasklet())
				.build();
	}

	@Bean
	public Job starWarsJob(Step step1, Step step2, Step step3) throws Exception {
		return jobBuilderFactory.get("starWarsJob")
				.incrementer(new RunIdIncrementer())
				.start(step1)
				.next(step2)
				.next(step3)
				.build();
	}
}
