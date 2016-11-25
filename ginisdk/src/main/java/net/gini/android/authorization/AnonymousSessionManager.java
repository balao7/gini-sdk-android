package net.gini.android.authorization;


import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import bolts.Continuation;
import bolts.Task;

import static net.gini.android.Utils.checkNotNull;


/**
 * The AnonymousSessionManager is a SessionManager implementation that uses anonymous Gini users.
 */
public class AnonymousSessionManager implements SessionManager {

    /**
     * The UserCenterManager instance which is used to create and log in the anonymous users.
     */
    private final UserCenterManager mUserCenterManager;
    /**
     * The credentials store which is used to store the user credentials.
     */
    private final CredentialsStore mCredentialsStore;

    /**
     * The domain which is used as the e-mail domain for created users.
     */
    private final String mEmailDomain;

    /**
     * The user's current session.
     */
    private Session mCurrentSession;

    /** The current task to get a new session. */
    private Task<Session> mCurrentSessionTask;

    public AnonymousSessionManager(final String emailDomain, final UserCenterManager userCenterManager,
                                   final CredentialsStore credentialsStore) {
        mEmailDomain = checkNotNull(emailDomain);
        mUserCenterManager = checkNotNull(userCenterManager);
        mCredentialsStore = checkNotNull(credentialsStore);
    }

    private synchronized void setSession(Session session) {
        mCurrentSession = session;
    }

    private synchronized void setCurrentSessionTask(@Nullable final Task<Session> sessionTask) {
        mCurrentSessionTask = sessionTask;
    }

    @Override
    public Task<Session> getSession() {
        final Task<Session>.TaskCompletionSource completionSource = Task.create();

        // First of all, try to reuse an active session.
        synchronized (this) {
            if (mCurrentSession != null && !mCurrentSession.hasExpired()) {
                return Task.forResult(mCurrentSession);
            }
            if (mCurrentSessionTask != null) {
                return mCurrentSessionTask;
            }
            mCurrentSessionTask = completionSource.getTask();
        }
        // Otherwise log in the user and store the session.
        loginUser().continueWith(new Continuation<Session, Object>() {
            @Override
            public Object then(Task<Session> task) throws Exception {
                if (task.isFaulted()) {
                    setCurrentSessionTask(null);
                    completionSource.setError(task.getError());
                } else if (task.isCancelled()) {
                    setCurrentSessionTask(null);
                    completionSource.setCancelled();
                } else {
                    Session session = task.getResult();
                    setSession(session);
                    setCurrentSessionTask(null);
                    completionSource.setResult(session);
                }

                return null;
            }
        });

        return completionSource.getTask();
    }

    /**
     * Log in the user whose credentials are currently stored in the credentials store. If there are no stored user
     * credentials, this method will create a new user via the UserCenterManager and then log in the newly created
     * user.
     *
     * @return A task which will resolve to valid Session instance.
     */
    public Task<Session> loginUser() {
        // Wrap getting the user credentials in a task, because it is much easier to handle the creation of a new
        // user then.
        Task<UserCredentials> credentialsTask;
        UserCredentials userCredentials = mCredentialsStore.getUserCredentials();
        if (userCredentials != null) {
            credentialsTask = Task.forResult(userCredentials);
        } else {
            credentialsTask = createUser();
        }

        // And log in the user when the user credentials are available.
        return credentialsTask.onSuccessTask(new Continuation<UserCredentials, Task<Session>>() {
            @Override
            public Task<Session> then(Task<UserCredentials> task) throws Exception {
                return mUserCenterManager.loginUser(task.getResult());
            }
        });
    }

    /**
     * Creates a new user via the UserCenterManager. The user credentials of the freshly created user are then stored in
     * the credentials store.
     *
     * Warning: This method overwrites the credentials of an existing user.
     *
     * @return A task which will resolve to a UserCredentials instance which store the credentials of the freshly
     * created user.
     */
    protected Task<UserCredentials> createUser() {
        final String username = UUID.randomUUID().toString() + "@" + mEmailDomain;
        final String password = UUID.randomUUID().toString();
        final UserCredentials userCredentials = new UserCredentials(username, password);
        return mUserCenterManager.createUser(userCredentials).onSuccess(new Continuation<User, UserCredentials>() {
            @Override
            public UserCredentials then(Task<User> task) throws Exception {
                mCredentialsStore.storeUserCredentials(userCredentials);
                return userCredentials;
            }
        });
    }
}