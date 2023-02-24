if [ $1 = 'clean' ]
then
	echo "clean old data"
	rm -rf cache/*.db && ls -alh cache | grep graphdb
	rm -rf rules/ignores.json && ls -alh rules | grep ignores
fi

echo "start to run tabby"
java -Xmx10g -jar tabby.jar