# Codexonia backend image
# Base: official Eclipse Temurin JDK 26 (matches your local JDK 26.0.2)
FROM eclipse-temurin:26-jdk

# Working directory inside the container
WORKDIR /app

# Copy only what the build needs (see .dockerignore)
COPY src/ src/
COPY test-vectors.json .

# Compile the Java sources the same way you do locally
RUN javac -d out src/main/java/HashFunction.java

# Default command: verify the shared test vectors
CMD ["java", "-cp", "out", "HashFunction"]
