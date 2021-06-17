package com.example.batchprocessing;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.json.JacksonJsonObjectReader;
import org.springframework.batch.item.json.JsonItemReader;
import org.springframework.batch.item.json.builder.JsonItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;

// tag::setup[]
@Configuration
@EnableBatchProcessing
public class BatchConfiguration {

	@Autowired
	public JobBuilderFactory jobBuilderFactory;

	@Autowired
	public StepBuilderFactory stepBuilderFactory;
	// end::setup[]

	// tag::readerwriterprocessor[]
	@Bean
	public JsonItemReader<Person> reader() {
		return new JsonItemReaderBuilder()
				.jsonObjectReader(new JacksonJsonObjectReader(Person.class))
				//.resource(new ClassPathResource("data1.json"))
				.name("personItemReader").build();
	}

//	@Bean
//	public ItemReader<String> reader() {
//
//		MultiResourceItemReader<String> reader = new MultiResourceItemReader<>();
//		reader.setResources(resources);
//		reader.setDelegate(new FlatFileItemReader<>(..));
//		return reader;
//	}

	@Bean
	public MultiResourceItemReader<Person> multiResourceItemReader()
	{

		Resource[] resources = null;
		ResourcePatternResolver patternResolver = new PathMatchingResourcePatternResolver();
		try {
			resources = patternResolver.getResources("/*.json");
		} catch (IOException e) {
			e.printStackTrace();
		}
		MultiResourceItemReader<Person> resourceItemReader = new MultiResourceItemReader<Person>();
		resourceItemReader.setResources(resources);
		resourceItemReader.setDelegate(reader());
		return resourceItemReader;
	}

//	@Bean
//	public FlatFileItemReader<Person> reader() {
//		return new FlatFileItemReaderBuilder<Person>()
//			.name("personItemReader")
//			.resource(new ClassPathResource("sample-data.csv"))
//			.delimited()
//			.names(new String[]{"firstName", "lastName"})
//			.fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
//				setTargetType(Person.class);
//			}})
//			.build();
//	}

	@Bean
	public PersonItemProcessor processor() {
		return new PersonItemProcessor();
	}

	@Bean
	public JdbcBatchItemWriter<Person> writer(DataSource dataSource) {
		return new JdbcBatchItemWriterBuilder<Person>()
			.itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
			.sql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)")
			.dataSource(dataSource)
			.build();
	}
	// end::readerwriterprocessor[]

	// tag::jobstep[]
	@Bean
	public Job importUserJob(JobCompletionNotificationListener listener, Step step1) {
		return jobBuilderFactory.get("importUserJob")
			.incrementer(new RunIdIncrementer())
			.listener(listener)
			.flow(step1)
			.end()
			.build();
	}

	@Bean
	public Step step1(JdbcBatchItemWriter<Person> writer) {
		return stepBuilderFactory.get("step1")
			.<Person, Person> chunk(10)
			.reader(multiResourceItemReader())
			.processor(processor())
			.writer(writer)
			.build();
	}
	// end::jobstep[]
}
