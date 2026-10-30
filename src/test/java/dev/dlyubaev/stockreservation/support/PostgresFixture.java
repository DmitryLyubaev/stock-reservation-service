package dev.dlyubaev.stockreservation.support;

import com.github.dockerjava.api.model.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class PostgresFixture {
  private static final int port = freePort();
  private static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(
              DockerImageName.parse(
                      "postgres:17.11-bookworm@sha256:839fbb5a4942a7758dd93e18b42ea5649807491a0545b5eeeb8f0b8bb11857a5")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("project20_test")
          .withUsername("demo")
          .withPassword("demo")
          .withCreateContainerCmdModifier(
              cmd ->
                  cmd.getHostConfig()
                      .withPortBindings(
                          new PortBinding(
                              Ports.Binding.bindIpAndPort("127.0.0.1", port),
                              new ExposedPort(5432))));

  private static int freePort() {
    try (var socket =
        new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
      return socket.getLocalPort();
    } catch (java.io.IOException failure) {
      throw new java.io.UncheckedIOException(failure);
    }
  }

  private PostgresFixture() {}

  public static synchronized void start() throws Exception {
    if (!postgres.isRunning()) postgres.start();
    var binding =
        postgres
            .getContainerInfo()
            .getNetworkSettings()
            .getPorts()
            .getBindings()
            .get(new ExposedPort(5432));
    if (binding == null || binding.length != 1 || !binding[0].getHostIp().equals("127.0.0.1"))
      throw new AssertionError("Test database must bind only to loopback");
    TransactionProbe.evidence(
        "postgres-fixture",
        java.util.Map.of(
            "container_id",
            postgres.getContainerId(),
            "session_id",
            postgres.getContainerInfo().getConfig().getLabels().get("org.testcontainers.sessionId"),
            "image",
            postgres.getDockerImageName(),
            "image_id",
            postgres.getContainerInfo().getImageId(),
            "host_ip",
            binding[0].getHostIp(),
            "host_port",
            binding[0].getHostPortSpec()));
  }

  public static void stopDatabaseProcess() {
    org.testcontainers.DockerClientFactory.instance()
        .client()
        .stopContainerCmd(postgres.getContainerId())
        .withTimeout(1)
        .exec();
  }

  public static void restartDatabaseProcess() {
    org.testcontainers.DockerClientFactory.instance()
        .client()
        .startContainerCmd(postgres.getContainerId())
        .exec();
    var actual =
        org.testcontainers.DockerClientFactory.instance()
            .client()
            .inspectContainerCmd(postgres.getContainerId())
            .exec()
            .getNetworkSettings()
            .getPorts()
            .getBindings()
            .get(new ExposedPort(5432));
    if (actual == null
        || actual.length != 1
        || !actual[0].getHostPortSpec().equals(Integer.toString(port)))
      throw new AssertionError("Owned database restart changed its explicit port");
  }

  public static String jdbcUrl() {
    return postgres.getJdbcUrl();
  }
}
