package com.github.mmolimar.kafka.connect.fs.file.reader.local;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.SchemaParseException;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;

import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.io.InvalidRecordException;

import org.junit.BeforeClass;
import org.junit.Test;

import com.github.mmolimar.kafka.connect.fs.file.Offset;
import com.github.mmolimar.kafka.connect.fs.file.reader.AgnosticFileReader;
import com.github.mmolimar.kafka.connect.fs.file.reader.ParquetFileReader;

public class ParquetFileReaderTest extends LocalFileReaderTestBase {

    private static final String FIELD_INDEX = "index";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_SURNAME = "surname";
    private static final String FILE_EXTENSION = "prqt";

    private static Schema readerSchema;
    private static Schema projectionSchema;

    @BeforeClass
    public static void setUp() throws IOException {
        readerClass = AgnosticFileReader.class;
        dataFile = createDataFile();
        readerConfig = new HashMap<String, Object>() {
			private static final long serialVersionUID = -1332618765409756822L;

		{
            put(AgnosticFileReader.FILE_READER_AGNOSTIC_EXTENSIONS_PARQUET, FILE_EXTENSION);
        }};
    }

    private static Path createDataFile() throws IOException {
        File parquetFile = File.createTempFile("test-", "." + FILE_EXTENSION);
        readerSchema = new Schema.Parser().parse(
                ParquetFileReaderTest.class.getResourceAsStream("/file/reader/schemas/people.avsc"));
        projectionSchema = new Schema.Parser().parse(
                ParquetFileReaderTest.class.getResourceAsStream("/file/reader/schemas/people_projection.avsc"));

        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(new Path(parquetFile.toURI()))
                .withConf(fs.getConf()).withWriteMode(ParquetFileWriter.Mode.OVERWRITE).withSchema(readerSchema).build()) {

            IntStream.range(0, NUM_RECORDS).forEach(index -> {
                GenericRecord datum = new GenericData.Record(readerSchema);
                datum.put(FIELD_INDEX, index);
                datum.put(FIELD_NAME, String.format("%d_name_%s", index, UUID.randomUUID()));
                datum.put(FIELD_SURNAME, String.format("%d_surname_%s", index, UUID.randomUUID()));
                try {
                    OFFSETS_BY_INDEX.put(index, Long.valueOf(index));
                    writer.write(datum);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            });
        }
        Path path = new Path(new Path(fsUri), parquetFile.getName());
        fs.moveFromLocalFile(new Path(parquetFile.getAbsolutePath()), path);
        return path;
    }

    @Test
    public void readerWithSchema() throws Throwable {
        Map<String, Object> cfg = new HashMap<String, Object>() {
			private static final long serialVersionUID = 4479153391833291739L;
		{
            put(ParquetFileReader.FILE_READER_PARQUET_SCHEMA, readerSchema.toString());
            put(AgnosticFileReader.FILE_READER_AGNOSTIC_EXTENSIONS_PARQUET, getFileExtension());
        }};
        reader = getReader(FileSystem.newInstance(fsUri, new Configuration()), dataFile, cfg);
        readAllData();
    }

    @Test(expected = DataException.class)
    public void readerWithProjection() throws Throwable {
        Map<String, Object> cfg = new HashMap<String, Object>() {
			private static final long serialVersionUID = 8546253284943826890L;
		{
            put(ParquetFileReader.FILE_READER_PARQUET_PROJECTION, projectionSchema.toString());
            put(AgnosticFileReader.FILE_READER_AGNOSTIC_EXTENSIONS_PARQUET, getFileExtension());
        }};
        reader = getReader(FileSystem.newInstance(fsUri, new Configuration()), dataFile, cfg);
        while (reader.hasNext()) {
            Struct record = reader.next();
            assertNotNull(record.schema().field(FIELD_INDEX));
            assertNotNull(record.schema().field(FIELD_NAME));
            assertNull(record.schema().field(FIELD_SURNAME));
        }

        reader = getReader(FileSystem.newInstance(fsUri, new Configuration()), dataFile, cfg);
        readAllData();
    }

    @Test(expected = InvalidRecordException.class)
    public void readerWithInvalidProjection() throws Throwable {
        Schema testSchema = SchemaBuilder.record("test_projection").namespace("test.avro")
                .fields()
                .name("field1").type("string").noDefault()
                .endRecord();
        Map<String, Object> cfg = new HashMap<String, Object>() {
			private static final long serialVersionUID = 2426225242931723289L;
		{
            put(ParquetFileReader.FILE_READER_PARQUET_PROJECTION, testSchema.toString());
            put(AgnosticFileReader.FILE_READER_AGNOSTIC_EXTENSIONS_PARQUET, getFileExtension());
        }};
        reader = getReader(FileSystem.newInstance(fsUri, new Configuration()), dataFile, cfg);
        readAllData();
    }

    @Test(expected = AvroRuntimeException.class)
    public void readerWithInvalidSchema() throws Throwable {
        Map<String, Object> cfg = new HashMap<String, Object>() {
			private static final long serialVersionUID = -1980400228039302014L;
		{
            put(ParquetFileReader.FILE_READER_PARQUET_SCHEMA, Schema.create(Schema.Type.STRING).toString());
            put(AgnosticFileReader.FILE_READER_AGNOSTIC_EXTENSIONS_PARQUET, getFileExtension());
        }};
        reader = getReader(FileSystem.newInstance(fsUri, new Configuration()), dataFile, cfg);
        readAllData();
    }

    @Test(expected = SchemaParseException.class)
    public void readerWithUnparseableSchema() throws Throwable {
        Map<String, Object> cfg = new HashMap<String, Object>() {
			private static final long serialVersionUID = -5487947363899477787L;
		{
            put(ParquetFileReader.FILE_READER_PARQUET_SCHEMA, "invalid schema");
            put(AgnosticFileReader.FILE_READER_AGNOSTIC_EXTENSIONS_PARQUET, getFileExtension());
        }};
        getReader(FileSystem.newInstance(fsUri, new Configuration()), dataFile, cfg);
    }

    @Override
    protected Offset getOffset(long offset) {
        return new ParquetFileReader.ParquetOffset(offset);
    }

    @Override
    protected void checkData(Struct record, long index) {
        assertTrue((Integer) record.get(FIELD_INDEX) == index);
        assertTrue(record.get(FIELD_NAME).toString().startsWith(index + "_"));
        assertTrue(record.get(FIELD_SURNAME).toString().startsWith(index + "_"));
    }

    @Override
    protected String getFileExtension() {
        return FILE_EXTENSION;
    }

} // ParquetFileReaderTest
