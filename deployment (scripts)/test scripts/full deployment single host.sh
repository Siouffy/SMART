weave launch
weave expose 10.0.0.1/24

#[IO] starting a single zk, single broker kafka cluster and a single rmq broker ...
weave run 10.0.0.11/24 -p 2181:2181 wurstmeister/zookeeper

weave run 10.0.0.12/24 -p 9092:9092 -e KAFKA_ADVERTISED_HOST_NAME="10.0.0.12" -e KAFKA_ZOOKEEPER_CONNECT="10.0.0.11:2181" -e KAFKA_BROKER_ID=2 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="false" -v /var/run/docker.sock:/var/run/docker.sock  wurstmeister/kafka:0.8.2.0

weave run 10.0.0.21/24 -p 5672:5672 rabbitmq:3.5.3

#creates a local cassandra cluster of a single node and a local lightning server for SmartState Testing

weave run 10.0.0.31/24 -p 9042:9042 -e IP=10.0.0.31 rugdsdev/env:cassandra-2.1.5-1-latest

weave run 10.0.0.41/24 -p 3000:3000 deardooley/lightning-viz
