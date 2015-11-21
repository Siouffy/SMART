sudo apt-get update
sudo apt-get upgrade


#installing Docker
wget -qO- https://get.docker.com/ | sh
sudo usermod -aG docker ubuntu
#logout / login 

#installing Weave
sudo curl -L git.io/weave -o /usr/local/bin/weave
sudo chmod a+x /usr/local/bin/weave

#launching weave and giving the other nodes ip addresses 
weave launch 172.31.40.163 172.31.40.165
weave expose 10.0.0.2/24


#kafka broker
weave run 10.0.0.12/24 -p 9092:9092 -e KAFKA_ADVERTISED_HOST_NAME="10.0.0.12" -e KAFKA_ZOOKEEPER_CONNECT="10.0.0.11:2181" -e KAFKA_BROKER_ID=2 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="false" -v /var/run/docker.sock:/var/run/docker.sock  wurstmeister/kafka:0.8.2.0

#cassandra node
weave run 10.0.0.32/24 -p 9042:9042 -e IP=10.0.0.32 -e SEEDS=10.0.0.31,10.0.0.32 rugdsdev/env:cassandra-2.1.5-1-latest

#spark worker
weave run 10.0.0.52/24 -t -e SPARK_LOCAL_IP=10.0.0.52 -e SPARK_MASTER_IP=10.0.0.51 siouffy/smart:2.11 /start-worker.sh

#spark submit [DRIVER]
weave run 10.0.0.60/24 -t -e SPARK_LOCAL_IP=10.0.0.60 siouffy/smart:2.11 
