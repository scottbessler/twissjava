package example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import me.prettyprint.cassandra.serializers.LongSerializer;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.cassandra.serializers.UUIDSerializer;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.ColumnSlice;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.beans.Row;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.mutation.Mutator;
import me.prettyprint.hector.api.query.MultigetSliceQuery;
import me.prettyprint.hector.api.query.QueryResult;
import me.prettyprint.hector.api.query.SliceQuery;

import org.apache.wicket.MetaDataKey;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.CSSPackageResource;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.protocol.http.WebSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.ContextLoaderListener;

import example.models.Timeline;
import example.models.Tweet;
import example.models.User;

/**
 * Base contains both the default header/footer things for the UI as
 *   well as all the shared code for all the child controllers.
 */
public abstract class Base extends WebPage {
    final static Logger log = LoggerFactory.getLogger(Base.class);

    final static Cluster cluster = 
      (Cluster)ContextLoaderListener.getCurrentWebApplicationContext().getBean("twissjavaCluster");
    
    final static Keyspace keyspace = 
      (Keyspace)ContextLoaderListener.getCurrentWebApplicationContext().getBean("twissjavaKeyspace");
    
    final static EntityManagerFactory entityManagerFactory = 
      (EntityManagerFactory)ContextLoaderListener.getCurrentWebApplicationContext().getBean("cassandraEntityManagerFactory");
                                                          
    final static StringSerializer ss = StringSerializer.get();
    final static LongSerializer ls = LongSerializer.get();
    final static UUIDSerializer us = UUIDSerializer.get();

    //Column Family names
    final static String USERS = "User";
    final static String FRIENDS = "Friends";
    final static String FOLLOWERS = "Followers";
    final static String TWEETS = "Tweet";
    final static String TIMELINE = "Timeline";
    final static String USERLINE = "Userline";

    //UI settings
    public Base(final PageParameters parameters) {
        add(CSSPackageResource.getHeaderContribution(Base.class, "960.css"));
        add(CSSPackageResource.getHeaderContribution(Base.class, "reset.css"));
        add(CSSPackageResource.getHeaderContribution(Base.class, "screen.css"));
        add(CSSPackageResource.getHeaderContribution(Base.class, "text.css"));
        
        String condauth = "Log";
        String username = ((TwissSession)WebSession.get()).getUname();
        if (username == null) {
            condauth += "in";
        }
        else {
            condauth += "out: " + username;
        }
        add(new Label("loginout", condauth));
    }

    //Helpers
    private Set<String> getFriendOrFollowerUnames(String columnFamily, String uname, int count) {
        SliceQuery<String,String,String> query = HFactory.createSliceQuery(keyspace, ss, ss, ss);
        query.setColumnFamily(columnFamily).setKey(uname).setRange(null, null, false, count);
        Set<String> friends = new HashSet<String>();
        QueryResult<ColumnSlice<String,String>> result = query.execute();
        for (HColumn<String, String> column : result.get().getColumns()) {
            friends.add(column.getName());
        }
        return friends;
    }

    private Timeline getLine(String columnFamily, String uname, String start, int count) {
        Long queryStart = start.isEmpty() ? null : Long.valueOf(start);
        SliceQuery<String,Long,UUID> query = HFactory.createSliceQuery(keyspace, ss, ls, us);
        query.setColumnFamily(columnFamily).setKey(uname).setRange(queryStart, null, true, count);
        QueryResult<ColumnSlice<Long, UUID>> result = query.execute();

        List<HColumn<Long, UUID>> columns = result.get().getColumns();
        Long mintimestamp = null;
        if (columns.size() == count) {
            mintimestamp = columns.get(columns.size() - 1).getName();
        }
        List<UUID> tweetids = new ArrayList<UUID>(columns.size());
        for (HColumn<Long, UUID> column : columns) {
            tweetids.add(column.getValue());
        }

        MultigetSliceQuery<UUID,String,String> mquery = HFactory.createMultigetSliceQuery(keyspace, us, ss, ss);
        mquery.setColumnFamily(TWEETS).setKeys(tweetids.toArray(new UUID[] {})).setColumnNames("body", "uname");
        // map-ify.  fu hector
        Map<UUID, List<HColumn<String, String>>> tweetColumns = new HashMap<UUID, List<HColumn<String, String>>>();
        for (Row<UUID, String, String> row : mquery.execute().get()) {
            tweetColumns.put(row.getKey(), row.getColumnSlice().getColumns());
        }
        //Order the tweets by the ordered tweetids
        ArrayList<Tweet> tweets = new ArrayList<Tweet>(tweetids.size());
        for (UUID tweetid : tweetids) {
            List<HColumn<String, String>> tcolumns = tweetColumns.get(tweetid);
            tweets.add(new Tweet(tweetid, tcolumns.get(1).getValue(), tcolumns.get(0).getValue()));
        }
        return new Timeline(tweets, mintimestamp);
    }


    //Data Reading
    public User getUserByUsername(String uname) {
        SliceQuery<String, String, String> query = HFactory.createSliceQuery(keyspace, ss, ss, ss);
        query.setColumnFamily(USERS).setKey(uname).setColumnNames("password");
        List<HColumn<String, String>> columns = query.execute().get().getColumns();
        if (columns.size() == 0) {
            log.info("User does not exist: " + uname);
            return null;
        }
        return new User(uname, columns.get(0).getValue());
    }

    public Set<String> getFriendUnames(String uname) {
        return getFriendUnames(uname, 5000);
    }
    public Set<String> getFriendUnames(String uname, int count) {
        return getFriendOrFollowerUnames(FRIENDS, uname, count);
    }

    public Set<String> getFollowerUnames(String uname) {
        return getFollowerUnames(uname, 5000);
    }
    public Set<String> getFollowerUnames(String uname, int count) {
        return getFriendOrFollowerUnames(FOLLOWERS, uname, count);
    }

    public Timeline getTimeline(String uname, Long startkey) {
        String longAsStr = (startkey == null) ? "" : String.valueOf(startkey);
        return getTimeline(uname, longAsStr, 40);
    }
    public Timeline getTimeline(String uname, String startkey, int limit) {
        return getLine(TIMELINE, uname, startkey, limit);
    }

    public Timeline getUserline(String uname, Long startkey) {
        String longAsStr = (startkey == null) ? "" : String.valueOf(startkey);
        return getUserline(uname, longAsStr, 40);
    }
    public Timeline getUserline(String uname, String startkey, int limit) {
        return getLine(USERLINE, uname, startkey, limit);
    }

    //Data Writing
    public void saveUser(User user) {
      entityManagerFactory.createEntityManager().persist(user);     
    }

    public void saveTweet(Tweet tweet) {
        long timestamp = System.currentTimeMillis();
        //Insert the tweet into tweets cf
        entityManagerFactory.createEntityManager().persist(tweet);

        // TODO how to annotate timeline vs. userline?
        //Insert into the user's timeline and timeline
        Mutator<String> m2 = HFactory.createMutator(keyspace, ss);
        m2.addInsertion(tweet.getUname(), USERLINE, HFactory.createColumn(timestamp, tweet.getKey(), ls, us));
        m2.addInsertion(tweet.getUname(), TIMELINE, HFactory.createColumn(timestamp, tweet.getKey(), ls, us));
        //Insert into the public timeline
        m2.addInsertion("!PUBLIC!", USERLINE, HFactory.createColumn(timestamp, tweet.getKey(), ls, us));
        //Insert into all followers streams
        for (String uname : getFollowerUnames(tweet.getUname())) {
            m2.addInsertion(uname, TIMELINE, HFactory.createColumn(timestamp, tweet.getKey(), ls, us));
        }
        m2.execute();
    }

    public void addFriends(String from, List<String> to) {
        Mutator<String> mutator = HFactory.createMutator(keyspace, ss);
        for (String uname : to) {
            mutator.addInsertion(from, FRIENDS, HFactory.createStringColumn(uname, ""))
                   .addInsertion(uname, FOLLOWERS, HFactory.createStringColumn(from, ""));
        }
        mutator.execute();
    }

    public void removeFriends(String from, List<String> to) {
        Mutator<String> mutator = HFactory.createMutator(keyspace, ss);
        for (String uname : to) {
            mutator.addDeletion(from, FRIENDS, uname, ss)
                   .addDeletion(uname, FOLLOWERS, from, ss);
        }
        mutator.execute();
    }
}