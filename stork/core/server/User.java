package stork.core.server;

import java.net.IDN;
import java.security.*;
import java.util.*;

import stork.ad.*;
import stork.core.*;
import stork.cred.*;
import stork.feather.*;
import stork.scheduler.*;
import stork.util.*;

/**
 * A registered Stork user. Each user has their own view of the job queue,
 * transfer credential manager, login credentials, and user info.
 */
public abstract class User {
  public String email;
  public String hash;
  public String salt;
  public boolean validated = true;

  public LinkedList<URI> history;
  public Map<String,StorkCred> credentials;
  public List<UserJob> jobs = new LinkedList<UserJob>();

  // A job owned by this user.
  private class UserJob extends Job {
    public User user() { return User.this; }
    public Server server() { return User.this.server(); }
  }

  /** The minimum allowed password length. */
  public static final int PASS_LEN = 6;

  /** Create an anonymous user. */
  public User() { }

  /** Create a user with the given email and password. */
  public User(String email, String password) {
    this.email = email;
    setPassword(password);
  }

  /** Get the server this user belongs to. */
  public abstract Server server();

  /** Check if the given password is correct for this user. */
  public synchronized boolean checkPassword(String password) {
    return hash(password).equals(hash);
  }

  /** Set the password for this user. Checks password length and hashes. */
  public synchronized void setPassword(String pass) {
    if (pass == null || pass.isEmpty())
      throw new RuntimeException("No password was provided.");
    if (pass.length() < PASS_LEN)
      throw new RuntimeException("Password must be "+PASS_LEN+"+ characters.");
    salt = salt();
    hash = hash(pass);
  }

  /** Get an object containing information to return on login. */
  public Object getLoginCookie() {
    return new Object() {
      String email = User.this.email;
      String hash = User.this.hash;
      List history = User.this.history;
    };
  }

  /** Add a URL to a user's history. */
  public synchronized void addHistory(URI u) {
    if (!isAnonymous() && Config.global.max_history > 0) try {
      history.remove(u);
      while (history.size() > Config.global.max_history)
        history.removeLast();
      history.addFirst(u);
    } catch (Exception e) {
      // Just don't add it.
    }
  }

  /** Check if a user is anonymous. */
  public boolean isAnonymous() { return email == null; }

  /** Normalize an email string for comparison. */
  public static String normalizeEmail(String email) {
    String[] parts = email.split("@");
    if (parts.length != 2)
      throw new RuntimeException("Invalid email address.");
    return parts[0].toLowerCase()+"@"+IDN.toASCII(parts[1]).toLowerCase();
  }

  /** Get the normalized email address of this user. */
  public String normalizedEmail() {
    return normalizeEmail(email);
  }

  /** Generate a random salt using a secure random number generator. */
  public static String salt() { return salt(24); }

  /** Generate a random salt using a secure random number generator. */
  public static String salt(int len) {
    byte[] b = new byte[len];
    SecureRandom random = new SecureRandom();
    random.nextBytes(b);
    return StorkUtil.formatBytes(b, "%02x");
  }

  /** Hash a password with this user's salt. */
  public String hash(String pass) {
    return hash(pass, salt);
  }

  /** Hash a password with the given salt. */
  public static String hash(String pass, String salt) {
    try {
      String saltpass = salt+'\n'+pass;
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] digest = saltpass.getBytes("UTF-8");

      // Run the digest for two rounds.
      for (int i = 0; i < 2; i++)
        digest = md.digest(digest);

      return StorkUtil.formatBytes(digest, "%02x");
    } catch (Exception e) {
      throw new RuntimeException("Couldn't hash password.");
    }
  }

  /** Create a {@link Job} owned by this user. */
  public Job createJob(Request job) {
    UserJob uj = new UserJob();
    return Ad.marshal(job).unmarshal(uj);
  }

  /** Save a {@link Job} to this {@code User}'s {@code jobs} list. */
  public Job saveJob(Job job) {
    if (job instanceof UserJob && job.user() == this) {
      jobs.add((UserJob) job);
    } else {
      UserJob uj = new UserJob();
      Ad.marshal(job).unmarshal(uj);
      job = uj;
      jobs.add(uj);
    }
    job.jobId(jobs.size());
    return job;
  }

  /** Create a {@link Job} and add it to {@code jobs}. */
  public Job createAndSaveJob(Request job) {
    return saveJob(createJob(job));
  }
}