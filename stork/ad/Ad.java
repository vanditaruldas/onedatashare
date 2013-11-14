package stork.ad;

import java.util.*;
import java.io.*;
import java.lang.ref.*;
import java.lang.reflect.*;

// TODO: Generic serialization stuff.

public class Ad implements Serializable {
  static final long serialVersionUID = 5988172454007663702L;

  // An ad is either a list or a map, but never both. Never access these
  // directly, always access through list() or map().
  private Map<String, AdObject> map = null;
  private List<AdObject> list = null;

  // Make this ad a map if it's undetermined. Return the map.
  Map<String, AdObject> map() {
    return map(true);
  } Map<String, AdObject> map(boolean make) {
    if (list != null)
      throw new RuntimeException("cannot access ad as a map");
    if (map == null && make)
      map = new LinkedHashMap<String, AdObject>();
    return map;
  }

  // Make this ad a list if it's undetermined. Return the list.
  List<AdObject> list() {
    return list(true);
  } List<AdObject> list(boolean make) {
    if (map != null)
      throw new RuntimeException("cannot access ad as a list");
    if (list == null && make)
      list = new LinkedList<AdObject>();
    return list;
  }

  // Ad keys are interned using weak references.
  private static Map<String, SoftReference<String>> internMap =
    new WeakHashMap<String, SoftReference<String>>();
  public static String intern(String k) {
    if (k == null)
      return null;
    SoftReference<String> s = internMap.get(k);
    if (s == null || s.get() == null)
      internMap.put(k, s = new SoftReference<String>(k));
    return s.get();
  }

  // Create a new ad, plain and simple.
  public Ad() { }

  // Create a new ad from an array.
  public Ad(Object[] list) {
    addAll(Arrays.asList(list));
  }

  // Create a new ad from a list.
  public Ad(Collection<?> list) {
    addAll(list);
  }

  // Create a new ad from an iterable.
  public Ad(Iterable<?> iter) {
    addAll(iter);
  }

  // Create a new ad from a map.
  public Ad(Map<?,?> map) {
    addAll(map);
  }

  // Create a new ad that is the copy of another ad.
  public Ad(Ad ad) {
    addAll(ad);
  }

  // Create an ad with given key and value.
  public Ad(Object key, Object value) {
    this(); put(key, value);
  }

  // Static parser methods. These will throw runtime exceptions if there
  // is a parse error, and will return null if EOF is encountered
  // prematurely.
  public static Ad parse(CharSequence cs) {
    return parse(cs, false);
  } public static Ad parse(InputStream is) {
    return parse(is, false);
  } public static Ad parse(File f) {
    return parse(f, false);
  } public static Ad parse(Reader r) {
    return parse(r, false);
  }

  public static Ad parse(CharSequence cs, boolean body_only) {
    return new AdParser(cs, body_only).parse();
  } public static Ad parse(InputStream is, boolean body_only) {
    return new AdParser(is, body_only).parse();
  } public static Ad parse(File f, boolean body_only) {
    Reader r = null;
    try {
      return new AdParser(r = new FileReader(f), body_only).parse();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (r != null) try {
        r.close();
      } catch (Exception e) {
        // Ugh, whatever...
      }
    }
  } public static Ad parse(Reader r, boolean body_only) {
    return new AdParser(r, body_only).parse();
  }

  // Access methods
  // --------------
  // Each accessor can optionally have a default value passed as a second
  // argument to be returned if no entry with the given name exists.
  // All of these eventually synchronize on getObject.

  // Get an entry from the ad as a string. Default: null
  public String get(Object s) {
    return get(s, null);
  } public String get(Object s, String def) {
    AdObject o = getObject(s);
    return (o != null) ? o.asString() : def;
  }

  // Get an entry from the ad as an integer. Defaults to -1.
  public int getInt(Object s) {
    return getInt(s, -1);
  } public int getInt(Object s, int def) {
    AdObject o = getObject(s);
    return (o != null) ? o.asInt() : def;
  }

  // Get an entry from the ad as a long. Defaults to -1.
  public long getLong(Object s) {
    return getLong(s, -1);
  } public long getLong(Object s, long def) {
    AdObject o = getObject(s);
    return (o != null) ? o.asLong() : def;
  }

  // Get an entry from the ad as a double. Defaults to -1.
  public double getDouble(Object s) {
    return getDouble(s, -1);
  } public double getDouble(Object s, double def) {
    AdObject o = getObject(s);
    return (o != null) ? o.asDouble() : def;
  }

  // Get an entry from the ad as a Number object. Attempts to cast to a
  // number object if it's a string. Defaults to null.
  public Number getNumber(Object s) {
    return getNumber(s, null);
  } public Number getNumber(Object s, Number def) {
    AdObject o = getObject(s);
    return (o != null) ? o.asNumber() : def;
  }

  // Get an entry from the ad as a boolean. Returns true if the value is
  // a true boolean, a string equal to "true", or a number other than zero.
  // Returns def if key is an ad or is undefined. Defaults to false.
  public boolean getBoolean(Object s) {
    return getBoolean(s, false);
  } public boolean getBoolean(Object s, boolean def) {
    AdObject o = getObject(s);
    return (o != null) ? o.asBooleanValue() : def;
  }

  // Get an inner ad from this ad. Defaults to null.
  public Ad getAd(Object s) {
    return getAd(s, null);
  } public Ad getAd(Object s, Ad def) {
    AdObject o = getObject(s);
    return (o != null) ? o.asAd() : def;
  } public Ad[] getAds(Object s) {
    return getAll(Ad[].class, s);
  } public Ad[] getAds() {
    return getAll(Ad[].class);
  }

  // Get a value as a list of a given type. If the value is not an ad,
  // return a list containing just that value. If the key does not
  // exist, returns null.
  public AdObject[] getAll(Object s) {
    return getAll(null, s);
  } public <C> C getAll(Class<C> c) {
    assert c.isArray();
    return AdObject.wrap(this).asArray(c);
  } public <C> C getAll(Class<C> c, Object s) {
    assert c.isArray();
    AdObject o = getObject(s);
    return (o != null) ? o.asArray(c) : null;
  }

  // Look up an object by its key. Handles recursive ad lookups.
  public synchronized AdObject getObject(Object okey) {
    int i;
    Ad ad = this;

    if (okey == null) {
      throw new RuntimeException("null key given");
    } if (isEmpty()) {
      // This is so we don't determine the ad type just because of a get.
      return null;
    } if (isList()) {
      if (okey instanceof Integer) {
        AdObject o = list().get((Integer)okey);
        return o;
      } else {
        // Don't choke on an access, just pretend it's not there.
        return null;
      }
    } else {
      String key = okey.toString();
      while ((i = key.indexOf('.')) > 0) synchronized (ad) {
        String k1 = key.substring(0, i);
        AdObject o = ad.map().get(k1);
        if (o == null)
          return null;
        ad = o.asAd();
        key = key.substring(i+1);
      }

      // No more ads to traverse, get value.
      synchronized (ad) {
        return ad.map().get(key);
      }
    }
  }

  // Insertion methods
  // -----------------
  // Methods for putting values into an ad. Certain primitive types can
  // be stored as their wrapped equivalents to save space, since they are
  // still printed in a way that is compatible with the language.
  // All of these eventually synchronize on putObject.
  public Ad put(Object k, Object... v) {
    switch (v.length) {
      case 0 : return putObject(k);
      case 1 : return putObject(k, v[0]);
      default: return putObject(k, new Ad(v));
    }
  }

  // Use this to insert objects in the above methods. This takes care
  // of validating the key so accidental badness doesn't occur.
  synchronized Ad putObject(Object value) {
    if (value instanceof Map.Entry<?,?>) {
      Map.Entry<?,?> e = (Map.Entry<?,?>) value;
      return putObject(e.getKey(), e.getValue());
    } return putObject(null, value);
  } synchronized Ad putObject(Object okey, Object value) {
    int i;
    Ad ad = this;

    if (okey == null) {
      list().add(AdObject.wrap(value));
    } else {
      String key = okey.toString();
      // Keep traversing ads until we find the ad we need to insert into.
      while ((i = key.indexOf('.')) > 0) synchronized (ad) {
        String k1 = key.substring(0, i);
        AdObject o = ad.map().get(k1);
        if (o == null)
          ad.map().put(intern(k1), AdObject.wrap(ad = new Ad()));
        else 
          ad = o.asAd();
        key = key.substring(i+1);
      }
      
      // No more ads to traverse, insert object.
      synchronized (ad) {
        if (value != null)
          ad.map().put(intern(key), AdObject.wrap(value));
        else
          ad.map().remove(key);
      }
    } return this;
  }

  // Other methods
  // -------------
  // Methods to get information about and perform operations on the ad.

  public synchronized boolean isEmpty() {
    return size() == 0;
  }

  public synchronized void clear() {
    if (isList())
      list().clear();
    else if (isMap())
      map().clear();
  }

  // Clear the ad and make its type undetermined.
  public synchronized void reset() {
    list = null;
    map  = null;
  }

  public synchronized void putAll(Map<String, Object> m) {
    for (Map.Entry<String, Object> e : m.entrySet()) {
      putObject(e.getKey(), e.getValue());
    }
  }

  // Get the number of fields in this ad.
  public synchronized int size() {
    return isMap()  ?  map().size() :
           isList() ? list().size() : 0;
  }

  // Get the key set if this ad is a map.
  public synchronized Set<String> keySet() {
    if (isEmpty() || isList())
      return Collections.emptySet();
    return map().keySet();
  }

  // Check if fields or values are present in the ad.
  public synchronized boolean containsKey(Object key) {
    return has(key.toString());
  } public synchronized boolean has(String... keys) {
    return require(keys) == null;
  }

  public synchronized boolean containsValue(Object val) {
    return isMap()  ?  map().containsValue(val) :
           isList() ? list().contains(val) : false;
  }

  // Ensure that all fields are present in the ad. Returns the first
  // string which doesn't exist in the ad, or null if all are found.
  public synchronized String require(String... keys) {
    for (String k : keys) if (getObject(k) == null)
      return k;
    return null;
  }

  // Merge this ad and others into a new ad.
  public synchronized Ad merge(Ad... ads) {
    return new Ad(this).addAll(ads);
  }

  // Add all the entries from another ad.
  public synchronized Ad addAll(Ad... ads) {
    for (Ad ad : ads) {
      if (ad == null || ad.isEmpty())
        continue;
      if (ad.isMap())
        return addAll(ad.map());
      else if (ad.isList())
        return addAll(ad.list());
    } return this;
  }

  // Add all the entries from a map.
  public synchronized Ad addAll(Map<?,?> map) {
    return addAll(map.entrySet());
  }

  // Add all the entries from a list.
  public synchronized Ad addAll(Iterable<?> c) {
    for (Object o : c) putObject(o);
    return this;
  }

  // Remove fields from this ad.
  public synchronized Ad remove(String... k) {
    for (String s : k)
      removeKey(s);
    return this;
  }

  // Return a new ad containing only the specified keys.
  // How should this handle sub ads?
  public synchronized Ad filter(String... keys) {
    Ad a = new Ad();
    for (Object k : keys)
      a.putObject(k, getObject(k));
    return a;
  }

  // Rename a key in the ad. Does nothing if the key doesn't exist.
  public synchronized Ad rename(String o, String n) {
    Object obj = removeKey(o);
    if (obj != null)
      putObject(n, obj);
    return this;
  }

  // Trim strings in this ad, removing empty strings.
  public synchronized Ad trim() {
    if (isEmpty())
      return this;
    if (isList())
      return trimList();
    if (isMap())
      return trimMap();
    return this;
  } private synchronized Ad trimList() {
    Iterator<AdObject> it = list().iterator();
    while (it.hasNext()) {
      AdObject o = it.next();

      if (o.asObject() instanceof String) {
        String s = o.toString().trim();
        if (s.isEmpty())
          it.remove();
        else o.setObject(s);
      } else if (o.asObject() instanceof Ad) {
        o.asAd().trim();
      }
    } return this;
  } private synchronized Ad trimMap() {
    Iterator<Map.Entry<String, AdObject>> it = map().entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, AdObject> e = it.next();
      AdObject o = e.getValue();

      if (o.asObject() instanceof String) {
        String s = o.toString().trim();
        if (s.isEmpty())
          it.remove();
        else o.setObject(s);
      } else if (o.asObject() instanceof Ad) {
        o.asAd().trim();
      }
    } return this;
  }

  // Remove a key from the ad, returning the old value (or null).
  private synchronized Object removeKey(Object okey) {
    int i;
    Ad ad = this;

    // Quick and easy check.
    if (isEmpty())
      return null;

    // See if we're removing an index from the list.
    if (okey instanceof Integer && isList())
      return list().remove((Integer)okey);

    String key = okey.toString();

    // Keep traversing ads until we find ad we need to remove from.
    while ((i = key.indexOf('.')) > 0) synchronized (ad) {
      String k1 = key.substring(0, i);
      Object o = ad.map().get(k1);
      if (o instanceof Ad)
        ad = (Ad) o;
      else return this;
      key = key.substring(i+1);
    }

    // No more ads to traverse, remove key.
    synchronized (ad) {
      return ad.map().remove(key);
    }
  }

  // Two ads are equal if they have the same keys and the corresponding
  // values are equal.
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } if (o instanceof Ad) {
      Ad ad = (Ad)o;
      if (isList() && ad.isList())
        return list().equals(ad.list());
      else if (isMap() && ad.isMap())
        return map().equals(ad.map());
      return isEmpty() && ad.isEmpty();
    } else {
      return false;
    }
  }

  public int hashCode() {
    return isList() ? list().hashCode() :
           isMap()  ?  map().hashCode() : 0;
  }

  public boolean isList() {
    return list != null;
  } public boolean isMap() {
    return map != null;
  } public boolean isUndetermined() {
    return list == map;
  }

  // Marshalling
  // -----------
  // Methods for marshalling objects to/from ads.

  // Unmarshal this ad into an object. This operation can throw a runtime
  // exception. Should we reset fields if there's an exception?
  public synchronized <O> O unmarshal(O o) {
    return unmarshal(o, null);
  } protected synchronized <O> O unmarshal(O o, AdType t) {
    t = (t != null) ? t : new AdType(o.getClass());
    Class c = t.clazz();
    if (c == Ad.class) {
      ((Ad)o).addAll(this);
    } else if (o instanceof Map) {
      AdType kt = t.generics()[0];
      AdType vt = t.generics()[1];
      for (Map.Entry<String, AdObject> e : map(false).entrySet())
        ((Map)o).put(e.getKey(), e.getValue().as(vt));
    } else if (o instanceof Collection) {
      AdType vt = t.generics()[0];
      for (AdObject v : list(false))
        ((Collection)o).add(v.as(vt));
    } else if (t.isArray()) {
      AdType vt = t.component();
      int i = 0;
      for (AdObject v : list(false)) try {
        Array.set(o, i++, v.as(vt));
      } catch (ArrayIndexOutOfBoundsException e) {
        break;
      }
    } else for (AdMember f : t.fields()) try {
      f.set(o, getObject(f.name()).as(f));
    } catch (Exception e) {
      // Either ad had no such member or it was final and we couldn't set
      // it. Either way, we don't have to worry about it.
    } return o;
  }

  // Construct a new instance of a class and marshal into it.
  public <O> O unmarshalAs(Class<O> clazz) {
    return AdObject.wrap(this).as(clazz);
  }

  // Marshal an object into an ad.
  public static Ad marshal(Object o) {
    return marshal(o, new AdType(o.getClass()));
  } public static Ad marshal(Object o, AdType t) {
    try {
      if (o instanceof Ad) {
        return (Ad)o;
      } else if (o instanceof Map) {
        return new Ad((Map) o);
      } else if (o instanceof Collection) {
        return new Ad((Collection) o);
      } else if (o instanceof Iterable) {
        return new Ad((Iterable) o);
      } else if (o.getClass().isArray()) {
        return new Ad((Object[])o);
      } else {
        Ad ad = new Ad();
        for (AdMember f : t.fields())
          ad.put(f.name(), f.get(o));
        return ad;
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Get the field names of a type as a string array.
  public static String[] fieldsOf(Type t) {
    Set<String> set = new HashSet<String>();
    for (AdMember m : new AdType(t).fields())
      set.add(m.name());
    return set.toArray(new String[0]);
  }

  // Composition methods
  // ------------------
  // Obvious an ad as an ad it just itself.
  public Ad toAd() {
    return this;
  }

  // Represent this ad in some kind of nice format. I guess the ClassAd
  // format is easier on the eyes, so let's use that.
  public synchronized String toString(boolean pretty) {
    return toClassAd(pretty);
  } public synchronized String toString() {
    return toClassAd(true);
  }

  // Represent this ad as a JSON string.
  public synchronized String toJSON(boolean pretty) {
    return (pretty ? AdPrinter.JSON : AdPrinter.JSON_MIN).toString(this);
  } public synchronized String toJSON() {
    return toJSON(true);
  }

  // Represent this ad as a ClassAd string.
  public synchronized String toClassAd(boolean pretty) {
    return (pretty ? AdPrinter.CLASSAD : AdPrinter.CLASSAD_MIN).toString(this);
  } public synchronized String toClassAd() {
    return toClassAd(true);
  }

  private static class TestClass {
    LinkedList<String> sl;
    LinkedList<Integer> il;
    String[] sa;
    int[] ia;
  }

  // Testing method.
  public static void main(String args[]) {
    Ad ad = new Ad();
    ad.put("sl", new int[] { 1, 2, 3 });
    ad.put("il", new String[] { "1", "2", "3" });
    ad.put("sa", new int[] { 1, 2, 3 });
    ad.put("ia", new String[] { "1", "2", "3" });
    TestClass tc = ad.unmarshalAs(TestClass.class);
    System.out.println(Ad.marshal(tc));
    System.out.println(tc.sl);
    System.out.println(tc.il);
    System.out.println(tc.sa[0]+tc.sa[2]);
    System.out.println(tc.ia[0]+tc.ia[2]);
  }
}
