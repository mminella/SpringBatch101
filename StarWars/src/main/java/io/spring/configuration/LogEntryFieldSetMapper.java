package io.spring.configuration;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

public class LogEntryFieldSetMapper implements FieldSetMapper<LogEntry> {

	@Override
	public LogEntry mapFieldSet(FieldSet fieldSet) throws BindException {
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MMM/yyyy:kk:mm:ss Z");

		LogEntry entry = new LogEntry();

		entry.setIpAddress(fieldSet.readString(0));
		try {
			entry.setViewDate(formatter.parse(fieldSet.readString(2)));
		}
		catch (ParseException e) {
			e.printStackTrace();
		}

		entry.setRequestedUrl(fieldSet.readString(3));

		return entry;
	}
}
