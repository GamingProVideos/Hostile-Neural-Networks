Put the compiled Placebo 26.2 mod JAR here as:

`Placebo-26.2-10.0.2-26.2-port-dev.jar`

Use the actual mod JAR from Placebo's `build/libs` folder, not a sources JAR.
Then run `gradlew.bat clean build` from the Hostile Neural Networks root.
The JAR must have been built from the companion Placebo 26.2 port; a 26.1
Placebo JAR does not provide the required 26.2 APIs.
