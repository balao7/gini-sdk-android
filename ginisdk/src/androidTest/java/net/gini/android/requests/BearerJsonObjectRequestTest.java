package net.gini.android.requests;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;

import junit.framework.TestCase;

import net.gini.android.authorization.Session;
import net.gini.android.authorization.requests.BearerJsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class BearerJsonObjectRequestTest extends TestCase {
    public void testAcceptHeader() throws AuthFailureError {
        Session session = new Session("1234-5678-9012");
        BearerJsonObjectRequest request = new BearerJsonObjectRequest(Request.Method.GET, "https://example.com", null, session, null, null);

        Map<String, String> headers = request.getHeaders();
        assertEquals(headers.get("Accept"), "application/json");
    }

    public void testContentTypeHeader() throws AuthFailureError, JSONException {
        Session session = new Session("1234-5678-9012");
        JSONObject payload = new JSONObject();
        payload.put("foo", "bar");
        BearerJsonObjectRequest request = new BearerJsonObjectRequest(Request.Method.GET, "https://example.com", payload, session, null, null);

        assertEquals(request.getBodyContentType(), "application/json; charset=utf-8");
    }
}
