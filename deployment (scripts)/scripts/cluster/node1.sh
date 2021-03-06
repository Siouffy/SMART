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
weave launch 172.31.40.164 172.31.40.165
weave expose 10.0.0.1/24


#lightning server
weave run 10.0.0.41/24 -p 3000:3000 deardooley/lightning-viz

#zk server
weave run 10.0.0.11/24 -p 2181:2181 wurstmeister/zookeeper

#cassandra node
weave run 10.0.0.31/24 -p 9042:9042 -e IP=10.0.0.31 -e SEEDS=10.0.0.31,10.0.0.32 rugdsdev/env:cassandra-2.1.5-1-latest

#spark master
weave run 10.0.0.51/24 -p 8080:8080 -t -e SPARK_LOCAL_IP=10.0.0.51 siouffy/smart:2.11 /start-master.sh
