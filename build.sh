mvn package
mkdir -p build
cp target/NewRun.jar build/NewRun.jar
cp bin/* build
cd build
zip -9r ../newrun.zip *