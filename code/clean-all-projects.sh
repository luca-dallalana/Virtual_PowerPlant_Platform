find . -name ".terraform" -exec rm -r .terraform '{}' \;

cd microservices/Telemetry	
mvn clean
cd ../..

cd microservices/Prosumer	
mvn clean
cd ../..

cd microservices/AssetLink	
mvn clean
cd ../..

cd microservices/UtilityOperator
mvn clean
cd ../..

cd microservices/FlexibilityEvent
mvn clean
cd ../..

cd microservices/GridBalancingRecommendation
mvn clean
cd ../..

cd microservices/EnergyAnalytics
mvn clean
cd ../..

cd microservices/FlexibilityForecasting
mvn clean
cd ../..
