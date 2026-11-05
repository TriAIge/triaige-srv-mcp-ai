package br.com.triaige.mcpai.domain.exception;

/** rawObjectKey referenciado não existe no S3. Falha imediata, sem retry. */
public class RawObjectNotFoundException extends McpException {

    public RawObjectNotFoundException(String bucket, String objectKey) {
        super("RAW_OBJECT_NOT_FOUND", "RAW_OBJECT_NOT_FOUND: s3://" + bucket + "/" + objectKey);
    }
}
