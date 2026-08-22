package com.emgi.timeline.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ImageStore
{
    public static final String DEFAULT_EXTENSION = "png";

    private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9]{1,8}");

    private final Path directory;

    public ImageStore(Path directory)
    {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath();
    }

    public static ImageStore atDefaultLocation()
    {
        return new ImageStore(defaultImageDirectory());
    }

    public static Path defaultImageDirectory()
    {
        return Path.of(System.getProperty("user.home"), ".timeline", "images");
    }

    public Path directory()
    {
        return directory;
    }

    public URI store(byte[] bytes, String extension)
    {
        Objects.requireNonNull(bytes, "bytes");

        Path target = directory.resolve(UUID.randomUUID() + "." + safeExtension(extension));

        try
        {
            Files.createDirectories(directory);
            Files.write(target, bytes);
        }
        catch(IOException e)
        {
            throw new UncheckedIOException("Could not save the image to " + target, e);
        }

        return target.toUri();
    }

    public URI copyFrom(Path source)
    {
        Objects.requireNonNull(source, "source");

        Path target = directory.resolve(UUID.randomUUID() + "." + extensionOf(source));

        try
        {
            Files.createDirectories(directory);
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
        catch(IOException e)
        {
            throw new UncheckedIOException("Could not copy " + source + " into " + directory, e);
        }

        return target.toUri();
    }

    private static String extensionOf(Path source)
    {
        String name = source.getFileName() == null ? "" : source.getFileName().toString();
        int dot = name.lastIndexOf('.');

        return safeExtension(dot < 0 ? null : name.substring(dot + 1));
    }

    private static String safeExtension(String raw)
    {
        if(raw == null)
        {
            return DEFAULT_EXTENSION;
        }

        String lower = raw.strip().toLowerCase(Locale.ROOT);
        return SAFE_EXTENSION.matcher(lower).matches() ? lower : DEFAULT_EXTENSION;
    }
}
