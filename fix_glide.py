
import re

with open("app/build.gradle", "r") as f:
    content = f.read()

if "glide" not in content:
    content = content.replace("dependencies {", "dependencies {\n    implementation 'com.github.bumptech.glide:glide:4.16.0'\n    annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'")
    with open("app/build.gradle", "w") as f:
        f.write(content)
print("Done")

