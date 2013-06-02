package raven.client.connection;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;

//TODO: review me
public class HttpJsonRequestFactory  {
  public HttpJsonRequest createHttpJsonRequest(CreateHttpJsonRequestParams params) {
    HttpClient httpClient = params.getServerClient().getHttpClient();
    switch (params.getMethod()) {
    case GET:
      GetMethod getMethod = new GetMethod(params.getUrl());
      return new HttpJsonRequest(httpClient, getMethod);
    case POST:
      PostMethod postMethod = new PostMethod(params.getUrl());
      return new HttpJsonRequest(httpClient, postMethod);
    case PUT:
      PutMethod putMethod = new PutMethod(params.getUrl());
      return new HttpJsonRequest(httpClient, putMethod);
    case DELETE:
      DeleteMethod deleteMethod = new DeleteMethod(params.getUrl());
      return new HttpJsonRequest(httpClient, deleteMethod);

    default:
      throw new IllegalArgumentException("Unknown method: " + params.getMethod());
    }
  }
}
