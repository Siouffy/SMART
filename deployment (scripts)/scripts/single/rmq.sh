#ImageName: rabbitmq:3.5.3
#Broker IP on the Weave network: 10.0.0.21

weave run 10.0.0.21/24 -p 5672:5672 rabbitmq:3.5.3
