build-ResumeParserFunction:
	cd ../../../ && ./gradlew --no-daemon :serverless:phase1:functions:resume-parser-lambda:clean :serverless:phase1:functions:resume-parser-lambda:build :serverless:phase1:functions:resume-parser-lambda:copyRuntimeLibs
	mkdir -p $(ARTIFACTS_DIR)/lib
	cp build/libs/*.jar $(ARTIFACTS_DIR)/
	cp build/runtime-libs/*.jar $(ARTIFACTS_DIR)/lib/
