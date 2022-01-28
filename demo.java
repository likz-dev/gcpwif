package com.example.demo;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.signer.internal.AbstractAwsS3V4Signer;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4PresignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.time.Clock;

public class DemoWorkloadIdentityFederation {
    private static final String SERVICE_NAME = "sts";
    private static AbstractAwsS3V4Signer signer;
    public static final String REGION = "AP_SOUTHEAST_1";

    public static final Clock signingOverrideClock = Clock.systemUTC();;



    public static void main(String[] args) {

        String projectId = "xxxxxxxxx";

        String poolId = "xxxxxxxx";
        String providerId = "xxxxxxxx";
        String serviceAccount = "xxxxxxxxxx";
        String listUrl = "xxxxxxxxxxxx";

        SdkHttpFullRequest request = buildRequest(projectId, poolId, providerId).build();
        System.out.println("Request");
        System.out.println(request);
        System.out.println("----------------");
        SdkHttpFullRequest request_signed = createAWSToken(request);
        System.out.println("Signed request");
        System.out.println(request_signed.toBuilder().headers());
    }

    public static SdkHttpFullRequest createAWSToken(SdkHttpFullRequest request) {
        System.out.println("Creating aws token");

        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
        AwsCredentials credentials = credentialsProvider.resolveCredentials();

        Aws4Signer signer = Aws4Signer.create();
        Aws4PresignerParams presignerParams = Aws4PresignerParams.builder()
                .signingRegion(Region.of(REGION))
                .signingName(SERVICE_NAME)
                .signingClockOverride(signingOverrideClock)
                .awsCredentials(credentials)
                .build();

        System.out.println("presignerParams");
        System.out.println(presignerParams);

        SdkHttpFullRequest signedUrl = signer.sign(request, presignerParams);
        System.out.println(signedUrl.toBuilder().headers());
        return signedUrl;
    }

    public static SdkHttpFullRequest.Builder buildRequest(String projectId, String poolId, String providerId) {
        return SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.POST)
                .putHeader("Host", "sts.amazonaws.com")
                .putHeader("x-goog-cloud-target-resource", "//iam.googleapis.com/projects/" + projectId + "/locations/global/workloadIdentityPools/" + poolId + "/providers/" + providerId)
                .encodedPath("/")
                .uri(URI.create("https://sts.amazonaws.com/?Action=GetCallerIdentity&Version=2011-06-15"));
    }
}
