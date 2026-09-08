package br.com.triaige.mcpai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    private String region = "us-east-1";
    private Sqs sqs = new Sqs();
    private S3 s3 = new S3();
    private Textract textract = new Textract();

    @Data
    public static class Sqs {
        private String endpoint;
    }

    @Data
    public static class S3 {
        private String endpoint;
    }

    @Data
    public static class Textract {
        /** LocalStack Community não suporta Textract — deixar em branco fora de perfis com mock próprio. */
        private String endpoint;
    }
}
