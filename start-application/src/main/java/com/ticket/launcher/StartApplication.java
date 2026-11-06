package com.ticket.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class StartApplication {
    public static void main(String[] args) {
        boolean stopping = args.length > 0 && "stop".equalsIgnoreCase(args[0]);
        Path projectRoot = findProjectRoot(Path.of(System.getProperty("user.dir")));
        List<String> command = stopping
                ? List.of("docker", "compose", "down")
                : List.of("docker", "compose", "up", "--build", "-d");

        try {
            int exitCode = new ProcessBuilder(command)
                    .directory(projectRoot.toFile())
                    .inheritIO()
                    .start()
                    .waitFor();

            if (exitCode != 0) {
                throw new IllegalStateException("Docker Compose завершился с кодом " + exitCode);
            }

            System.out.println(stopping
                    ? "Все сервисы остановлены."
                    : "Сервисы запущены. Сайт: http://localhost:5500");
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось запустить Docker Compose. Проверь, что Docker Desktop запущен.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запуск Docker Compose был прерван.", exception);
        }
    }

    private static Path findProjectRoot(Path start) {
        Path current = start.toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("docker-compose.yml"))) {
                return current;
            }
            current = current.getParent();
        }

        throw new IllegalStateException("Не найден docker-compose.yml в текущей папке и её родителях.");
    }
}