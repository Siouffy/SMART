weave launch
weave expose 10.0.0.1/24

#weave run 10.0.0.11/24 -p 2181:2181 wurstmeister/zookeeper
#weave run 10.0.0.12/24 -p 9092:9092 -e KAFKA_ADVERTISED_HOST_NAME="10.0.0.12" -e KAFKA_ZOOKEEPER_CONNECT="10.0.0.11:2181" -e KAFKA_BROKER_ID=2 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="false" -v /var/run/docker.sock:/var/run/docker.sock  wurstmeister/kafka:0.8.2.0

weave run 10.0.0.21/24 -p 5672:5672 rabbitmq:3.5.3

weave run 10.0.0.31/24 -p 9042:9042 -e IP=10.0.0.31 -e SEEDS=10.0.0.31 rugdsdev/env:cassandra-2.1.5-1-latest

weave run 10.0.0.41/24 -p 3000:3000 deardooley/lightning-viz


###standalone spark cluster [single master] [2x workers]
###since we're already running a local cluster, it would be better to skip these and use spark submit with masterURL = local[*] i.e. GOTO XX
#################################################################################################################################################
#weave run 10.0.0.51/24 -t -e SPARK_LOCAL_IP=10.0.0.51 siouffy/spark /start-master.sh				    #############################
#weave run 10.0.0.52/24 -t -e SPARK_LOCAL_IP=10.0.0.52 -e SPARK_MASTER_IP=10.0.0.51 siouffy/spark /start-worker.sh #############################
#weave run 10.0.0.53/24 -t -e SPARK_LOCAL_IP=10.0.0.53 -e SPARK_MASTER_IP=10.0.0.51 siouffy/spark /start-worker.sh #############################
#################################################################################################################################################

### ==> XX
#weave run 10.0.0.60/24 -t -e SPARK_LOCAL_IP=10.0.0.60 siouffy/spark 

##perform these manually 
## 1- docker exec -it 'shell-container-name' bash
###2- ./usr/local/spark/bin/spark-shell -i 10.0.0.60  [ADD: spark submit command]
