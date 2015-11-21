#ZK ImageName: wurstmeister/zookeeper
#ZK IP on the Weave network: 10.0.0.11
#Broker ImageName: wurstmeister/kafka:0.8.2.0
#Brokers IP on the Weave network: 10.0.0.12, 10.0.0.13

weave run 10.0.0.11/24 -p 2181:2181 wurstmeister/zookeeper
weave run 10.0.0.12/24 -p 9092:9092 -e KAFKA_ADVERTISED_HOST_NAME="10.0.0.12" -e KAFKA_ZOOKEEPER_CONNECT="10.0.0.11:2181" -e KAFKA_BROKER_ID=2 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="false" -v /var/run/docker.sock:/var/run/docker.sock  wurstmeister/kafka:0.8.2.0
weave run 10.0.0.13/24 -p 9092:9092 -e KAFKA_ADVERTISED_HOST_NAME="10.0.0.13" -e KAFKA_ZOOKEEPER_CONNECT="10.0.0.11:2181" -e KAFKA_BROKER_ID=3 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="false" -v /var/run/docker.sock:/var/run/docker.sock  wurstmeister/kafka:0.8.2.0

