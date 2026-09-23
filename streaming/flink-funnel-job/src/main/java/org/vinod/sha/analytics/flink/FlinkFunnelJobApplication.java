package org.vinod.sha.analytics.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.vinod.shared.analytics.events.AnalyticsEvent;
import org.vinod.shared.analytics.events.AnalyticsTopics;

public class FlinkFunnelJobApplication {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"))
                .setTopics(AnalyticsTopics.RAW_V1)
                .setGroupId("sha-flink-funnel-job")
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<AnalyticsEvent> events = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "analytics-raw-source")
                .map(json -> objectMapper.readValue(json, AnalyticsEvent.class));

        events
                .map(event -> "[analytics] type=" + event.getEventType()
                        + " candidateId=" + event.getCandidateId()
                        + " jobId=" + event.getJobId()
                        + " stage=" + event.getStage()
                        + " status=" + event.getStatus())
                .print();

        env.execute("sha-flink-funnel-job");
    }
}
