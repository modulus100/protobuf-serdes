package dev.alma.protobuf.serdes;

import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

final class S3DescriptorBytesLoader implements DescriptorBytesLoader {

    private final S3Client s3Client;
    private final String bucket;
    private final String keyTemplate;

    S3DescriptorBytesLoader(S3Client s3Client, String bucket, String keyTemplate) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.keyTemplate = keyTemplate;
    }

    @Override
    public byte[] load(String subject, int version) {
        String key = keyTemplate
            .replace("{subject}", subject)
            .replace("{version}", Integer.toString(version));

        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();
        try {
            return s3Client.getObject(request, ResponseTransformer.toBytes()).asByteArray();
        } catch (S3Exception e) {
            throw new IllegalStateException(
                "Failed to fetch protobuf descriptor from s3://" + bucket + "/" + key,
                e
            );
        }
    }

    @Override
    public void close() {
        s3Client.close();
    }
}
