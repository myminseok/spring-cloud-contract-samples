package docs;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Generates a JSON file that will be used to sketch
 * the dependencies between projects
 *
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
public class GenerateGraphFromContractsTests {
	private final ObjectMapper mapper = new ObjectMapper();

	@Value("classpath:contracts") Resource contracts;

	@Test
	public void should_build_a_list_of_relationships() throws IOException {
		Path root = Paths.get(contracts.getURI());
		DependencyWalker dependencyWalker = new DependencyWalker(root);

		Files.walkFileTree(root, dependencyWalker);

		File outputFile = new File("/relationships.json");
		outputFile.createNewFile();
		String relationships = mapper.writeValueAsString(dependencyWalker.relationships());
		Files.write(outputFile.toPath(), relationships.getBytes());
	}
}


class Relationship {
	public String parent;
	public String child;

	public Relationship(String parent, String child) {
		this.parent = parent;
		this.child = child;
	}
}

class DependencyWalker extends SimpleFileVisitor<Path> {

	private static final Logger log = LoggerFactory.getLogger(DependencyWalker.class);
	private static final Pattern SEMVER = Pattern.compile("^(\\d+\\.)?(\\d+\\.)?(\\*|\\d+)$");

	private final List<String> foundProducers = new ArrayList<>();
	private final List<String> foundConsumers = new ArrayList<>();
	private final List<Relationship> relationships = new ArrayList<>();
	private final Path root;

	DependencyWalker(Path root) {
		this.root = root;
	}

	List<String> foundProducers() {
		return this.foundProducers;
	}

	List<String> foundConsumers() {
		// TODO: print which consumers are not matched
		return this.foundConsumers;
	}

	List<Relationship> relationships() {
		return this.relationships;
	}

	@Override public FileVisitResult preVisitDirectory(Path dir,
			BasicFileAttributes attrs) throws IOException {
		File producer = dir.toFile();
		boolean pomExists = new File(producer, "pom.xml").exists();
		boolean gradleExists = new File(producer, "build.gradle").exists();
		if (pomExists || gradleExists) {
			// com/example/producer/1.0.0 -> com/example
			// com/example/producer -> com/example
			Path artifactId = isSemver(dir) ? dir.getParent() : dir;
			Path artifactIdParent = artifactId.getParent();
			String version = isSemver(dir) ? dir.getFileName().toString() : "";
			Path relativePath = this.root.relativize(artifactIdParent);
			// com/example -> com.example
			String relativeGroupId = relativePath.toString().replace(File.separator, ".");
			String producerName = artifactId.getFileName().toString();
			// com.example:producer
			String ga = relativeGroupId + ":" + producerName;
			File[] consumers = producer.listFiles(File::isDirectory);
			if (consumers != null) {
				Arrays.stream(consumers).forEach(file -> {
					String consumerName = file.getName();
					this.foundConsumers.add(consumerName);
					this.relationships.add(new Relationship(ga, consumerName));
				});
			}
			log.info("Found [" + ga + "] producer with consumers [" +
					Arrays.toString(consumers) + "]");
			return FileVisitResult.SKIP_SUBTREE;
		}
		return super.preVisitDirectory(dir, attrs);
	}

	private boolean isSemver(Path file) {
		//TODO: Consider adding some file like .version for non semver
		return SEMVER.matcher(file.getFileName().toString()).matches();
	}

}