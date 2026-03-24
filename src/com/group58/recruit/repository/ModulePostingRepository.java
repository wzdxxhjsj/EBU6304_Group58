package com.group58.recruit.repository;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.group58.recruit.config.AppPaths;
import com.group58.recruit.model.ModulePosting;

public final class ModulePostingRepository {

    private static final String FILE_NAME = "modules.json";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Type listType = new TypeToken<List<ModulePosting>>() {
    }.getType();

    private Path filePath() {
        return AppPaths.dataDirectory().resolve(FILE_NAME);
    }

    public List<ModulePosting> findAll() {
        Path path = filePath();
        if (!Files.isRegularFile(path)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<ModulePosting> list = gson.fromJson(reader, listType);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void saveAll(List<ModulePosting> postings) throws IOException {
        Path path = filePath();
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(postings, listType, writer);
        }
    }
}
