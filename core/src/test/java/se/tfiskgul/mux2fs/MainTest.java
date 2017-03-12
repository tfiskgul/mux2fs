package se.tfiskgul.mux2fs;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.beust.jcommander.ParameterException;

import ru.serce.jnrfuse.FuseException;

public class MainTest extends Fixture {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Test
	public void testHelp()
			throws Exception {
		Main.main(array("-h"));
	}

	@Test
	public void testInvalidOptions()
			throws Exception {
		exception.expect(ParameterException.class);
		Main.main(array("nonse -o nse c -p ommand line"));
	}

	@Test
	public void testInvalidFuseOptions()
			throws Exception {
		exception.expect(FuseException.class);
		Main.main(array("/", "/tmp", "-o", "tempdir=/tmp,nonsense=invalid"));
	}

	@Test
	public void testVersion()
			throws Exception {
		Main.main(array("-v"));
	}
}
