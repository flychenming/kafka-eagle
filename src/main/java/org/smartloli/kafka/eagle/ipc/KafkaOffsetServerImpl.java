/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartloli.kafka.eagle.ipc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.UUID;

import org.apache.thrift.TException;
import org.smartloli.kafka.eagle.util.ConstantUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import kafka.common.OffsetAndMetadata;
import kafka.server.GroupTopicPartition;

/**
 * Implements kafka rpc api.
 * 
 * @author smartloli.
 *
 *         Created by Jan 5, 2017
 * 
 * @see org.smartloli.kafka.eagle.ipc.KafkaOffsetServer
 */
public class KafkaOffsetServerImpl extends KafkaOffsetGetter implements KafkaOffsetServer.Iface {

	/** According to group, topic & partition to get the topic data in Kafka. */
	@Override
	public String query(String group, String topic, int partition) throws TException {
		// TODO Auto-generated method stub
		return null;
	}

	/** Get offset in Kafka topic. */
	@Override
	public String getOffset() throws TException {
		JSONArray targets = new JSONArray();
		Map<String, Boolean> activer = getActiver();
		for (Entry<GroupTopicPartition, OffsetAndMetadata> entry : kafkaConsumerOffsets.entrySet()) {
			JSONObject object = new JSONObject();
			object.put("group", entry.getKey().group());
			object.put("topic", entry.getKey().topicPartition().topic());
			object.put("partition", entry.getKey().topicPartition().partition());
			object.put("offset", entry.getValue().offset());
			object.put("timestamp", entry.getValue().timestamp());
			String key = entry.getKey().group() + ConstantUtils.Separator.EIGHT + entry.getKey().topicPartition().topic() + ConstantUtils.Separator.EIGHT + entry.getKey().topicPartition().partition();
			if (activer.containsKey(key)) {
				UUID uuid = UUID.randomUUID();
				String threadId = String.format("%s-%d-%s-%d", entry.getKey().group(), System.currentTimeMillis(), (uuid.getMostSignificantBits() + "").substring(0, 8), entry.getKey().topicPartition().partition());
				object.put("owner", threadId);
			} else {
				object.put("owner", "");
			}
			targets.add(object);
		}
		return targets.toJSONString();
	}

	/** Using SQL to get data from Kafka in topic. */
	@Override
	public String sql(String sql) throws TException {
		// TODO Auto-generated method stub
		return null;
	}

	/** Get consumer from Kafka in topic. */
	@Override
	public String getConsumer() throws TException {
		Map<String, Set<String>> targets = new HashMap<>();
		for (Entry<GroupTopicPartition, OffsetAndMetadata> entry : kafkaConsumerOffsets.entrySet()) {
			String group = entry.getKey().group();
			String topic = entry.getKey().topicPartition().topic();
			if (targets.containsKey(group)) {
				Set<String> set = targets.get(group);
				set.add(topic);
			} else {
				Set<String> set = new HashSet<>();
				set.add(topic);
				targets.put(group, set);
			}
		}
		/** Convert Set to List. */
		Map<String, List<String>> targets2 = new HashMap<>();
		for (Entry<String, Set<String>> entry : targets.entrySet()) {
			List<String> topics = new ArrayList<>();
			for (String topic : entry.getValue()) {
				topics.add(topic);
			}
			targets2.put(entry.getKey(), topics);
		}
		return targets2.toString();
	}

	/** Get activer from Kafka in topic. */
	private Map<String, Boolean> getActiver() throws TException {
		long mill = System.currentTimeMillis();
		Map<String, Boolean> active = new ConcurrentHashMap<>();
		for (Entry<GroupTopicPartition, OffsetAndMetadata> entry : kafkaConsumerOffsets.entrySet()) {
			String group = entry.getKey().group();
			String topic = entry.getKey().topicPartition().topic();
			int partition = entry.getKey().topicPartition().partition();
			long timespan = entry.getValue().timestamp();
			String key = group + ConstantUtils.Separator.EIGHT + topic + ConstantUtils.Separator.EIGHT + partition;
			if (active.containsKey(key)) {
				if ((mill - timespan) <= ConstantUtils.Kafka.ACTIVER_INTERVAL) {
					active.put(key, true);
				} else {
					active.put(key, false);
				}
			} else {
				active.put(key, true);
			}
		}
		return active;
	}

	/** Get active consumer from Kafka in topic. */
	@Override
	public String getActiverConsumer() throws TException {
		long mill = System.currentTimeMillis();
		for (Entry<GroupTopicPartition, OffsetAndMetadata> entry : kafkaConsumerOffsets.entrySet()) {
			String group = entry.getKey().group();
			String topic = entry.getKey().topicPartition().topic();
			int partition = entry.getKey().topicPartition().partition();
			long timespan = entry.getValue().timestamp();
			String key = group + ConstantUtils.Separator.EIGHT + topic + ConstantUtils.Separator.EIGHT + partition;
			if (kafkaActiveConsumers.containsKey(key)) {
				if ((mill - timespan) <= ConstantUtils.Kafka.ACTIVER_INTERVAL) {
					kafkaActiveConsumers.put(key, true);
				} else {
					kafkaActiveConsumers.put(key, false);
				}
			} else {
				kafkaActiveConsumers.put(key, true);
			}
		}

		Map<String, Set<String>> target = new HashMap<>();
		for (Entry<String, Boolean> entry : kafkaActiveConsumers.entrySet()) {
			if (entry.getValue()) {
				String[] keys = entry.getKey().split(ConstantUtils.Separator.EIGHT);
				String key = keys[0] + "_" + keys[1];
				String topic = keys[1];
				if (target.containsKey(key)) {
					target.get(key).add(topic);
				} else {
					Set<String> topics = new HashSet<>();
					topics.add(topic);
					target.put(key, topics);
				}
			}
		}

		Map<String, List<String>> target2 = new HashMap<>();
		for (Entry<String, Set<String>> entry : target.entrySet()) {
			List<String> topics = new ArrayList<>();
			for (String topic : entry.getValue()) {
				topics.add(topic);
			}
			target2.put(entry.getKey(), topics);
		}

		return target2.toString();
	}

	/** Get consumer page data from Kafka in topic. */
	@Override
	public String getConsumerPage(String search, int iDisplayStart, int iDisplayLength) throws TException {
		Map<String, Set<String>> targets = new HashMap<>();
		int offset = 0;
		for (Entry<GroupTopicPartition, OffsetAndMetadata> entry : kafkaConsumerOffsets.entrySet()) {
			String group = entry.getKey().group();
			String topic = entry.getKey().topicPartition().topic();
			if (search.length() > 0 && search.equals(group)) {
				if (targets.containsKey(group)) {
					Set<String> topics = targets.get(group);
					topics.add(topic);
				} else {
					Set<String> topics = new HashSet<>();
					topics.add(topic);
					targets.put(group, topics);
				}
				break;
			} else if (search.length() == 0) {
				if (offset < (iDisplayLength + iDisplayStart) && offset >= iDisplayStart) {
					if (targets.containsKey(group)) {
						Set<String> topics = targets.get(group);
						topics.add(topic);
					} else {
						Set<String> topics = new HashSet<>();
						topics.add(topic);
						targets.put(group, topics);
					}
				}
				offset++;
			}
		}
		Map<String, List<String>> targets2 = new HashMap<>();
		for (Entry<String, Set<String>> entry : targets.entrySet()) {
			List<String> topics = new ArrayList<>();
			for (String topic : entry.getValue()) {
				topics.add(topic);
			}
			targets2.put(entry.getKey(), topics);
		}
		return targets2.toString();
	}

}
