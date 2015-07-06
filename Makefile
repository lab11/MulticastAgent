all:
	javac MultcastSocketAgent.java

run:
	java -Djava.net.preferIPv4Stack=true MulticastSocketAgent
