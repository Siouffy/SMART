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
weave launch 172.31.40.163 172.31.40.164
weave expose 10.0.0.3/24


#kafka broker
weave run 10.0.0.13/24 -p 9092:9092 -e KAFKA_ADVERTISED_HOST_NAME="10.0.0.13" -e KAFKA_ZOOKEEPER_CONNECT="10.0.0.11:2181" -e KAFKA_BROKER_ID=3 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="false" -v /var/run/docker.sock:/var/run/docker.sock  wurstmeister/kafka:0.8.2.0

#RMQ broker
weave run 10.0.0.21/24 -p 5672:5672 rabbitmq:3.5.3

#2xspark worker
weave run 10.0.0.53/24 -t -e SPARK_LOCAL_IP=10.0.0.53 -e SPARK_MASTER_IP=10.0.0.51 siouffy/smart:2.11 /start-worker.sh
weave run 10.0.0.54/24 -t -e SPARK_LOCAL_IP=10.0.0.54 -e SPARK_MASTER_IP=10.0.0.51 siouffy/smart:2.11 /start-worker.sh
