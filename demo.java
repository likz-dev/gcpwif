package com.example.demo;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4PresignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;

public class DemoWorkloadIdentityFederation {

    public static void main(String[] args) {

        String projectId = "xxxxxx";

        String poolId = "xxxxxxx";
        String providerId = "xxxxxxxx";
        String serviceAccount = "xxxxxx.iam.gserviceaccount.com";
        String listUrl = "https://asia-southeast1-dialogflow.googleapis.com/v3/projects/xxxxxx/locations/asia-southeast1/agents";
        String awsRoleARN = "arn:aws:iam::xxxxxx:role/service-role/xxxxx";

        // Get AWS Token
        AwsCredentials credentials = assumeRoleCredentials(awsRoleARN);
        SdkHttpFullRequest request = buildRequest(projectId, poolId, providerId).build();
        SdkHttpFullRequest.Builder request_signed = createAWSToken(request, credentials);
        String awsToken = formatAwsToken(request_signed.headers());
        System.out.println(awsToken);

        // Get Google Workload Identity Federation token
        String gcpStsToken = getGcpStsToken(awsToken, projectId, poolId, providerId);
        System.out.println(gcpStsToken);

        // Get Google Service Account Token
        String gcpSaToken = getGcpSaToken(gcpStsToken, serviceAccount);
        System.out.println(gcpSaToken);

        // Get Dialogflow Agents
        String agents = getDialogFlowAgents(gcpSaToken, listUrl);
        System.out.println(agents);
    }

    public static String getDialogFlowAgents(String gcpSaToken, String url) {
        Header[] headers = {
                new BasicHeader("Authorization", "Bearer " + gcpSaToken)
        };

        try {
            return sendGet(url, headers).toString();
        } catch (Exception e) {
            System.out.println(e);
            return "error";
        }

    }

    public static String getGcpSaToken(String gcpStsToken, String serviceAccount) {
        Header[] headers = {
                new BasicHeader("content-type", "text/json; charset=utf-8"),
                new BasicHeader("Authorization", "Bearer " + gcpStsToken)
        };

        JSONArray scope = new JSONArray();
        scope.put("https://www.googleapis.com/auth/cloud-platform");

        JSONObject body = new JSONObject();
        body.put("scope", scope);
        body.put("lifetime", "3600s");

        try {
            return sendPost("https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/" + serviceAccount + ":generateAccessToken", headers, body.toString()).getString("accessToken");
        } catch (Exception e) {
            System.out.println(e);
            return "error";
        }
    }


    public static String getGcpStsToken(String awsToken, String projectId, String poolId, String providerId) {
        // add request parameter, form parameters
        Header[] headers = {new BasicHeader("content-type", "text/json; charset=utf-8")};

        JSONObject body = new JSONObject();
        body.put("audience", "//iam.googleapis.com/projects/" + projectId + "/locations/global/workloadIdentityPools/" + poolId + "/providers/" + providerId);
        body.put("grantType", "urn:ietf:params:oauth:grant-type:token-exchange");
        body.put("requestedTokenType", "urn:ietf:params:oauth:token-type:access_token");
        body.put("scope", "https://www.googleapis.com/auth/cloud-platform");
        body.put("subjectTokenType", "urn:ietf:params:aws:token-type:aws4_request");
        body.put("subjectToken", awsToken);

        try {
            return sendPost("https://sts.googleapis.com/v1/token", headers, body.toString()).getString("access_token");
        } catch (Exception e) {
            return "error";
        }
    }

    private static JSONObject sendPost(String url, Header[] headers, String body) throws Exception {
        System.out.println(body);
        HttpPost post = new HttpPost(url);

        post.setHeaders(headers);

        //pass the json string request in the entity
        HttpEntity entity = new ByteArrayEntity(body.getBytes("UTF-8"));
        post.setEntity(entity);
//        post.setEntity(new UrlEncodedFormEntity(body));

        try (CloseableHttpClient httpClient = HttpClients.createDefault(); CloseableHttpResponse response = httpClient.execute(post)) {
            String res = EntityUtils.toString(response.getEntity());
            System.out.println(res);

            return new JSONObject(res);
        }
    }

    private static JSONObject sendGet(String url, Header[] headers) throws Exception {
        HttpGet post = new HttpGet(url);

        post.setHeaders(headers);

        try (CloseableHttpClient httpClient = HttpClients.createDefault(); CloseableHttpResponse response = httpClient.execute(post)) {
            String res = EntityUtils.toString(response.getEntity());
            System.out.println(res);

            return new JSONObject(res);
        }

    }

    public static String formatAwsToken(Map<String, List<String>> request) {
        System.out.println(request);
        String url = "https://sts.amazonaws.com/?Action=GetCallerIdentity&Version=2011-06-15";
        String method = "POST";
        String authorization = request.get("Authorization").get(0);
        String host = request.get("Host").get(0);
        String xAmzDate = request.get("X-Amz-Date").get(0);
        String xAmzSecurityToken = request.get("X-Amz-Security-Token").get(0);
        String xGoogCloudTargetResource = request.get("x-goog-cloud-target-resource").get(0);

        JSONArray headers = new JSONArray();

        JSONObject headersAuthorization = new JSONObject();
        headersAuthorization.put("key", "Authorization");
        headersAuthorization.put("value", authorization);

        JSONObject headersHost = new JSONObject();
        headersHost.put("key", "Host");
        headersHost.put("value", host);

        JSONObject headersXAmzDate = new JSONObject();
        headersXAmzDate.put("key", "X-Amz-Date");
        headersXAmzDate.put("value", xAmzDate);

        JSONObject headersXAmzSecurityToken = new JSONObject();
        headersXAmzSecurityToken.put("key", "X-Amz-Security-Token");
        headersXAmzSecurityToken.put("value", xAmzSecurityToken);

        JSONObject headersXGoogCloudTargetResource = new JSONObject();
        headersXGoogCloudTargetResource.put("key", "x-goog-cloud-target-resource");
        headersXGoogCloudTargetResource.put("value", xGoogCloudTargetResource);

        headers.put(headersAuthorization);
        headers.put(headersHost);
        headers.put(headersXAmzDate);
        headers.put(headersXAmzSecurityToken);
        headers.put(headersXGoogCloudTargetResource);

        JSONObject subjectToken = new JSONObject();
        subjectToken.put("url", url);
        subjectToken.put("method", method);
        subjectToken.put("headers", headers);

        try {
            System.out.println(subjectToken.toString());
            return URLEncoder.encode(subjectToken.toString(), StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    public static AwsCredentials assumeRoleCredentials(String arn) {
        StsClient stsClient = StsClient.builder()
                .region(Region.of("ap-southeast-1"))
                .build();
        AssumeRoleRequest roleRequest = AssumeRoleRequest.builder()
                .roleArn(arn)
                .roleSessionName("DialogflowAdmin")
                .build();
        AssumeRoleResponse roleResponse = stsClient.assumeRole(roleRequest);
        Credentials sts_credentials = roleResponse.credentials();

        return AwsSessionCredentials.create(sts_credentials.accessKeyId(), sts_credentials.secretAccessKey(), sts_credentials.sessionToken());
    }

    public static SdkHttpFullRequest.Builder createAWSToken(SdkHttpFullRequest request, AwsCredentials credentials) {
        System.out.println("Creating aws token");

//        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
//        AwsCredentials credentials = credentialsProvider.resolveCredentials();

        Aws4Signer signer = Aws4Signer.create();
        Aws4PresignerParams presignerParams = Aws4PresignerParams.builder()
                .signingRegion(Region.of("us-east-1"))
                .signingName("sts")
                .signingClockOverride(Clock.systemUTC())
                .awsCredentials(credentials)
                .build();

        System.out.println("presignerParams");
        System.out.println(presignerParams);

        return signer.sign(request, presignerParams).toBuilder();
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
