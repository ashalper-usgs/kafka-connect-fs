package com.github.mmolimar.kafka.connect.fs.policy.hdfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.hadoop.fs.Path;
import org.junit.BeforeClass;

import com.github.mmolimar.kafka.connect.fs.FsSourceTaskConfig;
import com.github.mmolimar.kafka.connect.fs.file.reader.TextFileReader;
import com.github.mmolimar.kafka.connect.fs.policy.SimplePolicy;

public class SimplePolicyTest extends HdfsPolicyTestBase {

    @BeforeClass
    public static void setUp() throws IOException {
        directories = new ArrayList<Path>() {
			private static final long serialVersionUID = -4369436847448905978L;
		{
            add(new Path(fsUri.toString(), UUID.randomUUID().toString()));
            add(new Path(fsUri.toString(), UUID.randomUUID().toString()));
        }};
        for (Path dir : directories) {
            fs.mkdirs(dir);
        }

        Map<String, String> cfg = new HashMap<String, String>() {
			private static final long serialVersionUID = 5099668106647645552L;
		{
            String uris[] = directories.stream().map(dir -> dir.toString())
                    .toArray(size -> new String[size]);
            put(FsSourceTaskConfig.FS_URIS, String.join(",", uris));
            put(FsSourceTaskConfig.TOPIC, "topic_test");
            put(FsSourceTaskConfig.POLICY_CLASS, SimplePolicy.class.getName());
            put(FsSourceTaskConfig.FILE_READER_CLASS, TextFileReader.class.getName());
            put(FsSourceTaskConfig.POLICY_REGEXP, "^[0-9]*\\.txt$");
            put(FsSourceTaskConfig.POLICY_PREFIX_FS + "dfs.data.dir", "test");
            put(FsSourceTaskConfig.POLICY_PREFIX_FS + "fs.default.name", "test");
        }};
        taskConfig = new FsSourceTaskConfig(cfg);
    }
    
} // SimplePolicyTest
