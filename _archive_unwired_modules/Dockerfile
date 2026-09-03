# BIM Intent Compiler — BackOffice Server
# Multi-stage build: compile with Maven, run with slim JRE

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY orm-core/         orm-core/
COPY ORMSandbox/       ORMSandbox/
COPY DAGCompiler/      DAGCompiler/
COPY 2D_Layout/        2D_Layout/
COPY TopologyMaker/    TopologyMaker/
COPY BIM_COBOL/        BIM_COBOL/
COPY IFCtoBOM/         IFCtoBOM/
COPY BIMBackOffice/    BIMBackOffice/
COPY BonsaiBIMDesigner/ BonsaiBIMDesigner/
COPY library/          library/
RUN mvn package -q -DskipTests -pl bim-backoffice -am

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /src/BIMBackOffice/target/bim-backoffice-*.jar app.jar
COPY --from=build /src/BIMBackOffice/target/dependency/ lib/

# SQLite native lib
RUN apk add --no-cache sqlite curl

EXPOSE 9877
ENV BIM_LIBRARY_DIR=/data/library

ENTRYPOINT ["java", "-cp", "app.jar:lib/*", \
            "com.bim.backoffice.server.BackOfficeServer"]
CMD ["/data/library", "9877"]
