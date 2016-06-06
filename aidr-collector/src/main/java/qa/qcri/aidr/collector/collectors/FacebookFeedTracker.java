package qa.qcri.aidr.collector.collectors;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import qa.qcri.aidr.collector.beans.CollectionTask;
import qa.qcri.aidr.collector.beans.FacebookCollectionTask;
import qa.qcri.aidr.collector.beans.FacebookEntityType;
import qa.qcri.aidr.collector.utils.CollectorConfigurationProperty;
import qa.qcri.aidr.collector.utils.CollectorConfigurator;
import qa.qcri.aidr.collector.utils.GenericCache;
import qa.qcri.aidr.common.redis.LoadShedder;

import com.google.gson.Gson;

import facebook4j.Event;
import facebook4j.Facebook;
import facebook4j.FacebookException;
import facebook4j.FacebookFactory;
import facebook4j.Group;
import facebook4j.Ordering;
import facebook4j.Page;
import facebook4j.Post;
import facebook4j.Reading;
import facebook4j.ResponseList;
import facebook4j.conf.Configuration;
import facebook4j.conf.ConfigurationBuilder;
import facebook4j.internal.logging.Logger;
import facebook4j.internal.org.json.JSONException;
import facebook4j.internal.org.json.JSONObject;

public class FacebookFeedTracker implements Closeable {

	private static Logger logger = Logger.getLogger(FacebookFeedTracker.class);
	private static CollectorConfigurator configProperties = CollectorConfigurator.getInstance();
	private JedisPublisher publisher;
	private Facebook facebook;
	private FacebookCollectionTask task;
	private LoadShedder shedder;

	private static final int DEFAULT_OFFSET = 0;
	private static final int DEFAULT_LIMIT = 100;
	private static final Long SEVEN_DAYS_IN_MILLISECS = 7*24*60*60*1000L;

	private static String FIELDS_TO_FETCH = "id,updated_time,message_tags,scheduled_publish_time,"
			+ "created_time, full_picture,object_id,with_tags, is_published, "
			+ "from,to,message,picture,link,name,caption,description,source,properties,"
			+ "icon,actions,privacy,type,shares,status_type,place,story,"
			+ "application,targeting,likes.summary(true),comments.summary(true)";

	public FacebookFeedTracker(FacebookCollectionTask task) {

		logger.info("Waiting to aquire Jedis connection for collection " + task.getCollectionCode());
		Configuration config = task2configuration(task);
		this.publisher = JedisPublisher.newInstance();
		logger.info("Jedis connection acquired for collection " + task.getCollectionCode());

		String channelName = configProperties.getProperty(CollectorConfigurationProperty.COLLECTOR_CHANNEL) + "." + task.getCollectionCode();
		shedder = new LoadShedder(
				Integer.parseInt(configProperties.getProperty(CollectorConfigurationProperty.PERSISTER_LOAD_LIMIT)),
				Integer.parseInt(configProperties.getProperty(CollectorConfigurationProperty.PERSISTER_LOAD_CHECK_INTERVAL_MINUTES)), 
				true,channelName);

		this.facebook = new FacebookFactory(config).getInstance();
		this.task = task;
	}

	public void start() {
		new Thread(new Runnable() {
			public void run() {
				Boolean pqr = GenericCache.getInstance().getFbSyncObjMap(task.getCollectionCode());
				if(pqr == null){
					pqr = true;
				}
				synchronized (pqr) {
					GenericCache.getInstance().setFbSyncObjMap(task.getCollectionCode(), pqr);
					GenericCache.getInstance().setFbSyncStateMap(task.getCollectionCode(), 0);
					collectFacebookData();
				}
				
			}
		}).start();
	}

	public void close() throws IOException {
		facebook.shutdown();
		publisher.close();
		logger.info("AIDR-Fetcher: Collection stopped which was tracking ");
	}

	private static Configuration task2configuration(CollectionTask task) {
		ConfigurationBuilder builder = new ConfigurationBuilder();
		builder.setDebugEnabled(false)
		.setOAuthAppId(configProperties.getProperty(CollectorConfigurationProperty.FACEBOOK_CONSUMER_KEY))
		.setOAuthAppSecret(configProperties.getProperty(CollectorConfigurationProperty.FACEBOOK_CONSUMER_SECRET))
		.setJSONStoreEnabled(true)
		.setOAuthAccessToken(task.getAccessToken());

		Configuration configuration = builder.build();
		return configuration;
	}

	public void collectFacebookData() {

		this.publisher = JedisPublisher.newInstance();
		logger.info("Jedis connection acquired for collection " + task.getCollectionCode());

		Date toTimestamp = new Date();
		
		for(FacebookEntityType type : FacebookEntityType.values()) {
			if(GenericCache.getInstance().getFbSyncStateMap(task.getCollectionCode()) == 0){
				this.fetchPosts(toTimestamp, type);
			}
			else{
				GenericCache.getInstance().getFbSyncObjMap(task.getCollectionCode()).notifyAll();
				break;
			}
		}
	}

	private void fetchPosts(Date toTimestamp, FacebookEntityType type) {
		List<String> entityIds = new ArrayList<String>();

		try {
			switch(type) {
			case PAGE:
				entityIds = this.fetchPageIds();
				break;

			case EVENT:
				entityIds = this.fetchEventIds();
				break;

			case GROUP:
				entityIds = this.fetchGroupIds();
				break;
			}

		} catch (FacebookException e) {
			logger.error("Error in processing request for : " + type, e);
		}

		if(entityIds != null && !entityIds.isEmpty()) {
			processPost(toTimestamp, entityIds, type);
		}
	}

	private List<String> fetchPageIds() throws FacebookException {
		List<String> entityIds = new ArrayList<String>();
		int offset = 0;
		while(offset >= 0) {
			ResponseList<Page> pageList = null;
			if(GenericCache.getInstance().getFbSyncStateMap(task.getCollectionCode()) == 0){
				pageList = facebook.searchPages(task.getToTrack(), new Reading().fields("id").order(Ordering.CHRONOLOGICAL).limit(DEFAULT_LIMIT).offset(offset));
			}
			else{
				GenericCache.getInstance().getFbSyncObjMap(task.getCollectionCode()).notifyAll();
				break;
			}
			
			if(pageList != null) {
				for (Page page : pageList) {
					String id = page.getId();
					entityIds.add(id);
				}
			} 
			offset = pageList != null && pageList.size() == DEFAULT_LIMIT ? offset + DEFAULT_LIMIT : -1;
		}
		return entityIds;
	}

	private List<String> fetchEventIds() throws FacebookException {
		List<String> entityIds = new ArrayList<String>();
		int offset = 0;
		while(offset >= 0) {
			ResponseList<Event> eventList = null;
			if(GenericCache.getInstance().getFbSyncStateMap(task.getCollectionCode()) == 0){
				eventList = facebook.searchEvents(task.getToTrack(), new Reading().fields("id").order(Ordering.CHRONOLOGICAL).limit(DEFAULT_LIMIT).offset(offset));
			}
			else{
				GenericCache.getInstance().getFbSyncObjMap(task.getCollectionCode()).notifyAll();
				break;
			}
			if(eventList != null) {
				for (Event event : eventList) {
					String id = event.getId();
					entityIds.add(id);
				}
			}
			offset = eventList != null && eventList.size() == DEFAULT_LIMIT ? offset + DEFAULT_LIMIT : -1;
		}
		return entityIds;
	}

	private List<String> fetchGroupIds() throws FacebookException {
		List<String> entityIds = new ArrayList<String>();
		int offset = 0;
		while(offset >= 0) {
			ResponseList<Group> groupList = null;
			if(GenericCache.getInstance().getFbSyncStateMap(task.getCollectionCode()) == 0){
				groupList = facebook.searchGroups(task.getToTrack(), new Reading().fields("id").order(Ordering.CHRONOLOGICAL).limit(DEFAULT_LIMIT).offset(offset));
			}
			else{
				GenericCache.getInstance().getFbSyncObjMap(task.getCollectionCode()).notifyAll();
				break;
			}
			if(groupList != null) {
				for (Group group : groupList) {
					String id = group.getId();
					entityIds.add(id);
				}
			}

			offset = groupList != null && groupList.size() == DEFAULT_LIMIT ? offset + DEFAULT_LIMIT : -1;
		}

		return entityIds;
	}

	private void processPost(Date toTimestamp, List<String> entityIds, FacebookEntityType parent)
	{

		String channelName = configProperties.getProperty(CollectorConfigurationProperty.COLLECTOR_CHANNEL) 
				+ "." + task.getCollectionCode(); 

		Date since = new Date(System.currentTimeMillis() - SEVEN_DAYS_IN_MILLISECS);
		Gson gson = new Gson();

		if(task.getLastExecutionTime() != null) {
			since = task.getLastExecutionTime();
		}

		for (String parentId : entityIds) {
			int postsOffset = 0;
			if (GenericCache.getInstance().getFbSyncStateMap(task.getCollectionCode()) == 0) {
				while (postsOffset >= 0) {
					
					if (GenericCache.getInstance().getFbSyncStateMap(task.getCollectionCode()) == 0) {
						try {
							ResponseList<Post> feed = facebook.getFeed(
									parentId,
									new Reading().fields(FIELDS_TO_FETCH)
											.since(since).until(toTimestamp)
											.order(Ordering.CHRONOLOGICAL)
											.limit(DEFAULT_LIMIT)
											.offset(postsOffset));
							postsOffset = feed.size() == DEFAULT_LIMIT ? postsOffset
									+ DEFAULT_LIMIT
									: -1;
							for (Post post : feed) {
								try {
									if (shedder.canProcess()) {
										JSONObject aidrJson = new JSONObject();
										aidrJson.put("doctype", "facebook");
										aidrJson.put("crisis_code",
												task.getCollectionCode());
										aidrJson.put("crisis_name",
												task.getCollectionName());
										aidrJson.put("parent_type", parent
												.name().toLowerCase());

										JSONObject docJson = new JSONObject(
												gson.toJson(post));
										docJson.put("aidr", aidrJson);

										int likeCount = post.getLikes()
												.getSummary() != null ? post
												.getLikes().getSummary()
												.getTotalCount() : 0;
										docJson.put("likesCount", likeCount);
										int commentCount = post.getComments()
												.getSummary() != null ? post
												.getComments().getSummary()
												.getTotalCount() : 0;
										docJson.put("commentsCount",
												commentCount);

										publisher.publish(channelName,
												docJson.toString());
									}
								} catch (JSONException e) {
									logger.warn("Post error for parent id : "
											+ parentId + " and type : "
											+ parent);
								}
							}

							task.setLastExecutionTime(toTimestamp);
							GenericCache.getInstance().setFbConfigMap(
									task.getCollectionCode(), task);
							GenericCache.getInstance().incrCounter(
									task.getCollectionCode(),
									(long) feed.size());
							if (feed != null && feed.size() > 0) {
								String lastDownloadedDoc = feed.get(
										feed.size() - 1).getMessage();
								if (lastDownloadedDoc != null
										&& !lastDownloadedDoc.isEmpty()
										&& lastDownloadedDoc.length() > 500) {
									lastDownloadedDoc = lastDownloadedDoc
											.substring(0, 250) + "...";
								}

								GenericCache.getInstance()
										.setLastDownloadedDoc(
												task.getCollectionCode(),
												lastDownloadedDoc);
							}
						} catch (FacebookException e) {
							logger.warn("Exception while fetching feeds for id: "
									+ parentId);
							//TODO Handle rate limit and oauth exceptions
						}
					}else{
						GenericCache.getInstance().getFbSyncObjMap(task.getCollectionCode()).notifyAll();
						break;
					}
				}
			}else{
					GenericCache.getInstance().getFbSyncObjMap(task.getCollectionCode()).notifyAll();
					break;
				}
			}

		
	}
}
