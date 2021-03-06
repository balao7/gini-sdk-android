package net.gini.android.authorization;

import android.test.InstrumentationTestCase;

import com.android.volley.NetworkResponse;
import com.android.volley.VolleyError;

import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.OngoingStubbing;

import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import bolts.Task;

import static net.gini.android.Utils.CHARSET_UTF8;
import static net.gini.android.Utils.mapToUrlEncodedString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class AnonymousSessionManagerTests extends InstrumentationTestCase {
    private AnonymousSessionManager mAnonymousSessionSessionManager;
    private UserCenterManager mUserCenterManager;
    private CredentialsStore mCredentialsStore;
    private String mEmailDomain;

    @Override
    public void setUp() {
        // https://code.google.com/p/dexmaker/issues/detail?id=2
        System.setProperty("dexmaker.dexcache", getInstrumentation().getTargetContext().getCacheDir().getPath());

        mUserCenterManager = Mockito.mock(UserCenterManager.class);
        mCredentialsStore = Mockito.mock(CredentialsStore.class);
        mEmailDomain = "example.com";
        mAnonymousSessionSessionManager = new AnonymousSessionManager(mEmailDomain, mUserCenterManager, mCredentialsStore);
    }

    public void testConstructionWithNullReferencesThrowsNullPointerException() {
        try {
            new AnonymousSessionManager(null, null, null);
            fail("NullPointerException not thrown");
        } catch (NullPointerException ignored) {
        }

        try {
            new AnonymousSessionManager("foobar", null, null);
            fail("NullPointerException not thrown");
        } catch (NullPointerException ignored) {
        }

        try {
            new AnonymousSessionManager("foobar", mUserCenterManager, null);
            fail("NullPointerException not thrown");
        } catch (NullPointerException ignored) {
        }
    }

    public void testGetSessionShouldReturnTask() {
        // Task that will never complete. Sufficient for this test.
        Task<User>.TaskCompletionSource completionSource = Task.create();
        when(mUserCenterManager.createUser(any(UserCredentials.class))).thenReturn(completionSource.getTask());

        assertNotNull(mAnonymousSessionSessionManager.getSession());
    }

    public void testGetSessionShouldResolveToSession() throws InterruptedException {
        UserCredentials userCredentials = new UserCredentials(email("foobar"), "1234");
        when(mCredentialsStore.getUserCredentials()).thenReturn(userCredentials);
        when(mUserCenterManager.loginUser(userCredentials))
                .thenReturn(Task.forResult(new Session(UUID.randomUUID().toString(), new Date())));

        Task<Session> sessionTask = mAnonymousSessionSessionManager.getSession();
        sessionTask.waitForCompletion();

        assertNotNull(sessionTask.getResult());
    }

    private String email(String name) {
        return name +"@" + mEmailDomain;
    }

    public void testLoginUserShouldReturnTask() {
        // Task that will never complete. Sufficient for this test.
        Task<User>.TaskCompletionSource completionSource = Task.create();
        when(mUserCenterManager.createUser(any(UserCredentials.class))).thenReturn(completionSource.getTask());

        assertNotNull(mAnonymousSessionSessionManager.loginUser());
    }

    public void testLoginUserShouldResolveToSession() throws InterruptedException {
        UserCredentials userCredentials = new UserCredentials(email("foobar"), "1234");
        when(mCredentialsStore.getUserCredentials()).thenReturn(userCredentials);
        when(mUserCenterManager.loginUser(userCredentials))
                .thenReturn(Task.forResult(new Session(UUID.randomUUID().toString(), new Date())));

        Task<Session> loginTask = mAnonymousSessionSessionManager.loginUser();
        loginTask.waitForCompletion();

        assertNotNull(loginTask.getResult());
    }


    public void testThatNewUserCredentialsAreStored() throws InterruptedException {
        // TODO: The returned "created" user has another email address than the UserCredentials instance which is given
        //       to the mock.
        User fakeUser = new User("1234-5678-9012-3456", email("foobar"));
        when(mUserCenterManager.createUser(any(UserCredentials.class))).thenReturn(Task.forResult(fakeUser));

        Task<Session> sessionTask = mAnonymousSessionSessionManager.getSession();
        sessionTask.waitForCompletion();

        verify(mCredentialsStore).storeUserCredentials(any(UserCredentials.class));
    }

    public void testThatStoredUserCredentialsAreUsed() throws InterruptedException {
        UserCredentials userCredentials = new UserCredentials(email("foobar"), "1234");
        when(mCredentialsStore.getUserCredentials()).thenReturn(userCredentials);

        Session session = new Session("1234-5678-9012", new Date());
        when(mUserCenterManager.loginUser(userCredentials)).thenReturn(Task.forResult(session));

        Task<Session> sessionTask = mAnonymousSessionSessionManager.getSession();
        sessionTask.waitForCompletion();
        assertSame(session, sessionTask.getResult());
    }

    @SuppressWarnings("unchecked")
    public void testThatUserSessionsAreReused() throws InterruptedException {
        when(mCredentialsStore.getUserCredentials()).thenReturn(new UserCredentials(email("foo"), "1234"));
        when(mUserCenterManager.loginUser(any(UserCredentials.class))).thenReturn(
                Task.forResult(new Session(UUID.randomUUID().toString(), new Date(new Date().getTime() + 10000))),
                Task.forResult(new Session(UUID.randomUUID().toString(), new Date()))
        );

        Task<Session> firstSessionTask = mAnonymousSessionSessionManager.getSession();
        firstSessionTask.waitForCompletion();

        Task<Session> secondSessionTask = mAnonymousSessionSessionManager.getSession();
        secondSessionTask.waitForCompletion();

        assertSame(firstSessionTask.getResult(), secondSessionTask.getResult());
    }

    @SuppressWarnings("unchecked")
    public void testThatUserSessionsAreNotReusedWhenTimedOut() throws InterruptedException {
        when(mCredentialsStore.getUserCredentials()).thenReturn(new UserCredentials(email("foo"), "1234"));
        when(mUserCenterManager.loginUser(any(UserCredentials.class))).thenReturn(
                Task.forResult(new Session(UUID.randomUUID().toString(), new Date(new Date().getTime() - 10000))),
                Task.forResult(new Session(UUID.randomUUID().toString(), new Date()))
        );


        Task<Session> firstSessionTask = mAnonymousSessionSessionManager.getSession();
        firstSessionTask.waitForCompletion();

        assertTrue(firstSessionTask.getResult().hasExpired());

        Task<Session> secondSessionTask = mAnonymousSessionSessionManager.getSession();
        secondSessionTask.waitForCompletion();

        assertNotSame(firstSessionTask.getResult(), secondSessionTask.getResult());
    }

    public void testThatCreatedUserNamesAreEmailAddresses() throws InterruptedException {
        // TODO: The returned "created" user has another email address than the UserCredentials instance which is given
        //       to the mock.
        User fakeUser = new User("1234-5678-9012-3456", email("foobar"));
        when(mUserCenterManager.createUser(any(UserCredentials.class))).thenReturn(Task.forResult(fakeUser));

        Task<Session> sessionTask = mAnonymousSessionSessionManager.getSession();
        sessionTask.waitForCompletion();

        ArgumentCaptor<UserCredentials> userCredentialsCaptor = ArgumentCaptor.forClass(UserCredentials.class);
        verify(mUserCenterManager).createUser(userCredentialsCaptor.capture());

        assertTrue(userCredentialsCaptor.getValue().getUsername().endsWith("@" + mEmailDomain));
    }

    @SuppressWarnings("unchecked")
    public void testThatExistingUserIsDeletedAndNewUserIsCreatedIfExistingIsInvalid() throws InterruptedException {
        makeLoginRequestFailOnce()
                .thenReturn(Task.forResult(new Session(UUID.randomUUID().toString(), new Date())));

        Task<Session> sessionTask = mAnonymousSessionSessionManager.getSession();
        sessionTask.waitForCompletion();

        verify(mCredentialsStore).deleteUserCredentials();
        verify(mUserCenterManager).createUser(any(UserCredentials.class));
    }

    private OngoingStubbing<Task<Session>> makeLoginRequestFailOnce() {
        when(mCredentialsStore.getUserCredentials()).thenReturn(new UserCredentials(email("foo"), "1234"));
        String invalidGrantErrorJson = "{\"error\": \"invalid_grant\"}";
        return when(mUserCenterManager.loginUser(any(UserCredentials.class)))
                .thenReturn(Task.<Session>forError(new VolleyError(
                        new NetworkResponse(400, invalidGrantErrorJson.getBytes(CHARSET_UTF8), Collections.<String, String>emptyMap(), true))));
    }

    @SuppressWarnings({"unchecked", "ThrowableResultOfMethodCallIgnored"})
    public void testThatCreateUserErrorIsReturnedWhenNewUserIsCreatedIfExistingIsInvalid() throws InterruptedException {
        makeLoginRequestFailOnce();
        when(mUserCenterManager.createUser(any(UserCredentials.class)))
                .thenReturn(Task.<User>forError(new VolleyError(new NetworkResponse(503, null, Collections.singletonMap("Some-Header", "10"), true))));

        Task<Session> sessionTask = mAnonymousSessionSessionManager.getSession();
        sessionTask.waitForCompletion();

        assertTrue("Task should have faulted", sessionTask.isFaulted());
        assertEquals(503, ((VolleyError) sessionTask.getError()).networkResponse.statusCode);
        String headerValue = ((VolleyError) sessionTask.getError()).networkResponse.headers.get("Some-Header");
        assertNotNull("Task error should contain response header 'Some-Header'", headerValue);
        assertEquals("10", headerValue);
    }

    public void testHasUserCredentialsEmailDomainReturnsTrueIfUsernameEmailDomainIsSameAsEmailDomain() {
        UserCredentials userCredentials = new UserCredentials("1234@example.com", "1234");
        assertTrue(mAnonymousSessionSessionManager.hasUserCredentialsEmailDomain("example.com", userCredentials));
    }

    public void testHasUserCredentialsEmailDomainReturnsFalseIfUsernameEmailDomainIsNotSameAsEmailDomain() {
        UserCredentials userCredentials = new UserCredentials("1234@example.com", "1234");
        assertFalse(mAnonymousSessionSessionManager.hasUserCredentialsEmailDomain("beispiel.com", userCredentials));
    }

    public void testHasUserCredentialsEmailDomainReturnsFalseIfUsernameEmailDomainContainsEmailDomain() {
        UserCredentials userCredentials = new UserCredentials("1234@exampledomain.com", "1234");
        assertFalse(mAnonymousSessionSessionManager.hasUserCredentialsEmailDomain("domain.com", userCredentials));
    }

    public void testThatLoginUserUpdatesEmailDomainIfChanged() throws InterruptedException {
        String newEmailDomain = "beispiel.com";
        String oldEmailDomain = "example.com";
        mAnonymousSessionSessionManager = new AnonymousSessionManager(newEmailDomain, mUserCenterManager, mCredentialsStore);
        when(mCredentialsStore.getUserCredentials())
                .thenReturn(new UserCredentials("1234@" + oldEmailDomain, "5678"));
        when(mUserCenterManager.updateEmail(anyString(), anyString(), any(Session.class)))
                .thenReturn(Task.forResult(new JSONObject()));
        when(mUserCenterManager.loginUser(any(UserCredentials.class)))
                .thenReturn(Task.forResult(new Session("1234-5678-9012", new Date())))
                .thenReturn(Task.forResult(new Session("1234-5678-9012", new Date())));

        Task<Session> loginTask = mAnonymousSessionSessionManager.loginUser();
        loginTask.waitForCompletion();

        assertTrue(loginTask.isCompleted());
        verify(mCredentialsStore).deleteUserCredentials();

        ArgumentCaptor<UserCredentials> userCredentialsCaptor = ArgumentCaptor.forClass(UserCredentials.class);
        verify(mCredentialsStore).storeUserCredentials(userCredentialsCaptor.capture());

        UserCredentials newUserCredentials = userCredentialsCaptor.getValue();
        assertEquals(newEmailDomain, extractEmailDomain(newUserCredentials.getUsername()));
        assertEquals("5678", newUserCredentials.getPassword());
    }

    private String extractEmailDomain(String email) {
        String[] components = email.split("@");
        if (components.length > 1) {
            return components[1];
        }
        return "";
    }
}
