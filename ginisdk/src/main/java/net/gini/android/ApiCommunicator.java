package net.gini.android;

import android.graphics.Bitmap;

import com.android.volley.AuthFailureError;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.StringRequest;

import net.gini.android.authorization.Session;
import net.gini.android.authorization.requests.BearerJsonObjectRequest;
import net.gini.android.requests.BearerUploadRequest;

import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import bolts.Task;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static com.android.volley.Request.Method.DELETE;
import static com.android.volley.Request.Method.GET;
import static com.android.volley.Request.Method.POST;
import static com.android.volley.Request.Method.PUT;
import static net.gini.android.Utils.checkNotNull;
import static net.gini.android.Utils.mapToUrlEncodedString;


/**
 * The ApiCommunicator is responsible for communication with the Gini API. It only converts the server's responses to
 * more convenient objects (e.g. a JSON response to a JSONObject) but does not interpret the results in any way.
 * Therefore it is not recommended to use the ApiCommunicator directly, but to use the DocumentTaskManager instead which
 * provides much more convenient methods to work with the Gini API and uses defined models.
 */
public class ApiCommunicator {

    private final String mBaseUrl;
    private final RequestQueue mRequestQueue;


    public ApiCommunicator(final String mBaseUrl, final RequestQueue mRequestQueue) {
        this.mBaseUrl = checkNotNull(mBaseUrl);
        this.mRequestQueue = checkNotNull(mRequestQueue);
    }

    public Task<JSONObject> uploadDocument(final byte[] documentData, final String contentType,
                                           @Nullable final String documentName, @Nullable final String docTypeHint,
                                           final Session session) {

        final HashMap<String, String> requestQueryData = new HashMap<String, String>();
        if (documentName != null) {
            requestQueryData.put("filename", documentName);
        }
        if (docTypeHint != null) {
            requestQueryData.put("doctype", docTypeHint);
        }
        final String url = mBaseUrl + "documents/?" + mapToUrlEncodedString(requestQueryData);
        final RequestTaskCompletionSource<JSONObject> completionSource =
                RequestTaskCompletionSource.newCompletionSource();
        final BearerUploadRequest request =
                new BearerUploadRequest(POST, url, checkNotNull(documentData), checkNotNull(contentType),
                                        session.getAccessToken(), completionSource, completionSource);
        mRequestQueue.add(request);

        return completionSource.getTask();
    }

    public Task<JSONObject> getDocument(final String documentId, final Session session) {
        final String url = String.format("%sdocuments/%s", mBaseUrl, checkNotNull(documentId));
        final RequestTaskCompletionSource<JSONObject> completionSource =
                RequestTaskCompletionSource.newCompletionSource();
        final BearerJsonObjectRequest request =
                new BearerJsonObjectRequest(GET, url, null, checkNotNull(session), completionSource, completionSource);
        mRequestQueue.add(request);

        return completionSource.getTask();
    }

    public Task<JSONObject> getExtractions(final String documentId, final Session session) {
        final String url = String.format("%sdocuments/%s/extractions", mBaseUrl, checkNotNull(documentId));
        final RequestTaskCompletionSource<JSONObject> completionSource =
                RequestTaskCompletionSource.newCompletionSource();
        final BearerJsonObjectRequest request =
                new BearerJsonObjectRequest(GET, url, null, checkNotNull(session), completionSource, completionSource);
        mRequestQueue.add(request);

        return completionSource.getTask();
    }

    public Task<JSONObject> getIncubatorExtractions(final String documentId, final Session session) {
        final String url = String.format("%sdocuments/%s/extractions", mBaseUrl, checkNotNull(documentId));
        final RequestTaskCompletionSource<JSONObject>completionSource = RequestTaskCompletionSource.newCompletionSource();
        final BearerJsonObjectRequest request = new BearerJsonObjectRequest(GET, url, null, checkNotNull(session), completionSource, completionSource) {
            @Override
            public Map<String, String>getHeaders() throws AuthFailureError{
                Map<String, String> headers = super.getHeaders();
                // The incubator is discriminated from the "normal" extractions by the accept header.
                headers.put("Accept", "application/vnd.gini.incubator+json");
                return headers;
            }
        };
        mRequestQueue.add(request);

        return completionSource.getTask();
    }

    public Task<String> deleteDocument(final String documentId, final Session session) {
        final String accessToken = checkNotNull(session).getAccessToken();
        final String url = String.format("%sdocuments/%s", mBaseUrl, checkNotNull(documentId));
        final RequestTaskCompletionSource<String> completionSource = RequestTaskCompletionSource.newCompletionSource();
        final StringRequest request = new StringRequest(DELETE, url, completionSource, completionSource) {
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        mRequestQueue.add(request);

        return completionSource.getTask();
    }

    public Task<JSONObject> errorReportForDocument(final String documentId, @Nullable final String summary,
                                                   @Nullable final String description, final Session session) {
        final HashMap<String, String> requestParams = new HashMap<String, String>();
        requestParams.put("summary", summary);
        requestParams.put("description", description);
        final String url = String.format("%sdocuments/%s/errorreport?%s", mBaseUrl, checkNotNull(documentId),
                                         mapToUrlEncodedString(requestParams));
        final RequestTaskCompletionSource<JSONObject> completionSource =
                RequestTaskCompletionSource.newCompletionSource();
        final BearerJsonObjectRequest request =
                new BearerJsonObjectRequest(POST, url, null, checkNotNull(session), completionSource, completionSource);
        mRequestQueue.add(request);

        return completionSource.getTask();
    }

    public Task<JSONObject> sendFeedback(final String documentId, final JSONObject extractions, final Session session)
            throws JSONException {
        final String url = String.format("%sdocuments/%s/extractions", mBaseUrl, checkNotNull(documentId));
        final RequestTaskCompletionSource<JSONObject> completionSource =
                RequestTaskCompletionSource.newCompletionSource();
        final JSONObject requestData = new JSONObject();
        requestData.put("feedback", checkNotNull(extractions));
        final BearerJsonObjectRequest request =
                new BearerJsonObjectRequest(PUT, url, requestData, checkNotNull(session),
                                            completionSource, completionSource);
        mRequestQueue.add(request);

        return completionSource.getTask();
    }

    public Task<Bitmap> getPreview(final String documentId, final int pageNumber,
                                   PreviewSize previewSize, final Session session) {
        final String url = String.format("%sdocuments/%s/pages/%s/%s", mBaseUrl, checkNotNull(documentId), pageNumber,
                                         previewSize.getDimensions());
        final String accessToken = checkNotNull(session).getAccessToken();
        RequestTaskCompletionSource<Bitmap> completionSource = RequestTaskCompletionSource.newCompletionSource();
        final ImageRequest imageRequest = new ImageRequest(url, completionSource, 0, 0, ARGB_8888, completionSource) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Authorization", "BEARER " + accessToken);
                headers.put("Accept", "image/jpeg");
                return headers;
            }
        };
        mRequestQueue.add(imageRequest);

        return completionSource.getTask();
    }

    public enum PreviewSize {
        /** Medium sized image, maximum dimensions are 750x900. */
        MEDIUM("750x900"),
        /** Big image, maximum dimensions are 1280x1810 */
        BIG("1280x1810");

        private final String mDimensions;

        PreviewSize(final String dimensions) {
            mDimensions = dimensions;
        }

        public String getDimensions() {
            return mDimensions;
        }
    }
}
