package jannovar.pedigree;

import jannovar.common.Genotype;
import jannovar.exception.PedParseException;
import jannovar.exome.Variant;
import jannovar.genotype.GenotypeCall;
import jannovar.io.ReferenceDictionary;
import jannovar.reference.HG19RefDictBuilder;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the Pedigree class.
 *
 * There are further tests PedigreeARTest and PedigreeADTest that check more complex cases.
 *
 * @author Manuel Holtgrewe <manuel.holtgrewe@charite.de>
 */
public class PedigreeTest {

	/** this test uses this static hg19 reference dictionary */
	static final ReferenceDictionary refDict = HG19RefDictBuilder.build();

	// Simple list of persons to use in test.
	ArrayList<Person> familyOfFour = new ArrayList<Person>();

	// Helper function for constructing Variants from Genotypes for testing.
	private Variant constructVariant(Genotype... calls) {
		float dummyPhred = 100f;
		ArrayList<Genotype> lst = new ArrayList<Genotype>();
		for (Genotype g : calls)
			lst.add(g);
		GenotypeCall gc = new GenotypeCall(lst, null);
		Variant v = new Variant(refDict, 1, 1, "A", "C", gc, dummyPhred, "");

		return v;
	}

	@Before
	public void setUp() throws PedParseException {
		familyOfFour.add(new Person("FAMILY", "FATHER", null, null, "1", "0"));
		familyOfFour.add(new Person("FAMILY", "MOTHER", null, null, "2", "1"));
		familyOfFour.add(new Person("FAMILY", "SON", "FATHER", "MOTHER", "1", "1"));
		familyOfFour.add(new Person("FAMILY", "DAUGHTER", "FATHER", "MOTHER", "2", "2"));
	}

	// Simple tests on a single-sample pedigree as constructed with
	// Pedigree.constructSingleSamplePedigree().
	@Test
	public void testConstructSingleSamplePedigree() {
		Pedigree pedigree = Pedigree.constructSingleSamplePedigree("INDIVIDUAL");

		Assert.assertEquals("INDIVIDUAL", pedigree.getSingleSampleName());
		Assert.assertEquals(1, pedigree.getPedigreeSize());
		Assert.assertEquals(1, pedigree.getNumberOfIndividualsInPedigree());
		Assert.assertEquals(0, pedigree.getNumberOfParentsInPedigree());
		Assert.assertEquals(1, pedigree.getNumberOfAffectedsInPedigree());
		Assert.assertEquals(0, pedigree.getNumberOfUnaffectedsInPedigree());
		Assert.assertEquals("FAMILY:INDIVIDUAL[affected;male]", pedigree.getPedigreeSummary());

		Person p = pedigree.getPerson("INDIVIDUAL");
		Assert.assertEquals("FAMILY", p.getFamilyID());
		Assert.assertEquals(null, p.getFatherID());
		Assert.assertEquals(null, p.getMotherID());
		Assert.assertFalse(p.isMale());
		Assert.assertFalse(p.isFemale());
		Assert.assertTrue(p.isAffected()); // AFFECTED
		Assert.assertFalse(p.isUnaffected()); // AFFECTED
		Assert.assertTrue(p.isFounder());
	}

	@Test
	public void testPedigreeOnePerson() throws PedParseException {
		ArrayList<Person> pList = new ArrayList<Person>();
		pList.add(new Person("FAMILY", "PERSON", null, null, "2", "1"));
		Pedigree pedigree = new Pedigree(pList, "FAMILY");

		Assert.assertEquals("PERSON", pedigree.getSingleSampleName());
		Assert.assertEquals(1, pedigree.getPedigreeSize());
		Assert.assertEquals(1, pedigree.getNumberOfIndividualsInPedigree());
		Assert.assertEquals(0, pedigree.getNumberOfParentsInPedigree());
		Assert.assertEquals(1, pedigree.getNumberOfAffectedsInPedigree());
		Assert.assertEquals(0, pedigree.getNumberOfUnaffectedsInPedigree());
		Assert.assertEquals("FAMILY:PERSON[unaffected;female]", pedigree.getPedigreeSummary());

		Person p = pedigree.getPerson("PERSON");
		Assert.assertEquals("FAMILY", p.getFamilyID());
		Assert.assertEquals(null, p.getFatherID());
		Assert.assertEquals(null, p.getMotherID());
		Assert.assertFalse(p.isMale());
		Assert.assertTrue(p.isFemale());
		Assert.assertFalse(p.isAffected()); // UNAFFECTED
		Assert.assertTrue(p.isUnaffected()); // UNAFFECTED
		Assert.assertTrue(p.isFounder());
	}

	// Construct family of four and check the query functions.
	@Test
	public void testFamilyOfFourQueryFunctions() throws PedParseException {
		Pedigree pedigree = new Pedigree(this.familyOfFour, "FAMILY");

		Assert.assertFalse(pedigree.isNthPersonAffected(0));
		Assert.assertFalse(pedigree.isNthPersonAffected(1));
		Assert.assertFalse(pedigree.isNthPersonAffected(2));
		Assert.assertTrue(pedigree.isNthPersonAffected(3));

		Assert.assertTrue(pedigree.isNthPersonParentOfAffected(0));
		Assert.assertTrue(pedigree.isNthPersonParentOfAffected(1));
		Assert.assertFalse(pedigree.isNthPersonParentOfAffected(2));
		Assert.assertFalse(pedigree.isNthPersonParentOfAffected(3));

		Assert.assertTrue(pedigree.sampleIsRepresentedInPedigree("MOTHER"));
		Assert.assertFalse(pedigree.sampleIsRepresentedInPedigree("Klaus"));

		Assert.assertEquals(4, pedigree.getNumberOfIndividualsInPedigree());
		Assert.assertEquals(2, pedigree.getNumberOfParentsInPedigree());
		Assert.assertEquals(1, pedigree.getNumberOfAffectedsInPedigree());
		Assert.assertEquals(1, pedigree.getNumberOfUnaffectedsInPedigree()); // exludes parents of affected
	}

	// Construct family of four and check getPerson function.
	@Test
	public void testFamilyOfFour_isFunctions2() throws PedParseException {
		Pedigree pedigree = new Pedigree(this.familyOfFour, "FAMILY");
		Assert.assertEquals(familyOfFour.get(0), pedigree.getPerson("FATHER"));
		Assert.assertEquals(familyOfFour.get(1), pedigree.getPerson("MOTHER"));
		Assert.assertEquals(familyOfFour.get(2), pedigree.getPerson("SON"));
		Assert.assertEquals(familyOfFour.get(3), pedigree.getPerson("DAUGHTER"));
	}

	@Test
	public void testFamilyOfFour_adjustSampleOrderInPedFile() throws PedParseException {
		Pedigree pedigree = new Pedigree(this.familyOfFour, "FAMILY");

		ArrayList<String> sampleNames = new ArrayList<String>();
		sampleNames.add("DAUGHTER");
		sampleNames.add("SON");
		sampleNames.add("FATHER");
		sampleNames.add("MOTHER");

		pedigree.adjustSampleOrderInPedFile(sampleNames);

		Assert.assertEquals(pedigree.get(0), familyOfFour.get(3));
		Assert.assertEquals(pedigree.get(1), familyOfFour.get(2));
		Assert.assertEquals(pedigree.get(2), familyOfFour.get(0));
		Assert.assertEquals(pedigree.get(3), familyOfFour.get(1));
	}

	@Test
	public void testAddPerson() throws PedParseException {
		// TODO(holtgrem): private function initializeAffectedsParentsSibs is
		// not called on addPerson
		// TODO(holtgrem): also, used nowhere, could be removed
		Pedigree pedigree = Pedigree.constructSingleSamplePedigree("Person");

		pedigree.addIndividual(this.familyOfFour.get(0));

		Assert.assertTrue(pedigree.isNthPersonAffected(0));
		Assert.assertFalse(pedigree.isNthPersonAffected(1));

		Assert.assertFalse(pedigree.isNthPersonParentOfAffected(0));
		Assert.assertFalse(pedigree.isNthPersonParentOfAffected(1));

		Assert.assertTrue(pedigree.sampleIsRepresentedInPedigree("Person"));
		Assert.assertTrue(pedigree.sampleIsRepresentedInPedigree("FATHER"));
		Assert.assertFalse(pedigree.sampleIsRepresentedInPedigree("Klaus"));

		Assert.assertEquals(1, pedigree.getNumberOfIndividualsInPedigree());
		Assert.assertEquals(0, pedigree.getNumberOfParentsInPedigree());
		Assert.assertEquals(0, pedigree.getNumberOfUnaffectedsInPedigree()); // TODO(holtgrem): should be 1!
	}

	@Test
	public void testSingleSampleHasHeterozygousVariant() {
		Pedigree pedigree = Pedigree.constructSingleSamplePedigree("PERSON");
		ArrayList<Variant> varList = new ArrayList<Variant>();

		// homozygous alt -- no heterozygous variant
		varList.add(constructVariant(Genotype.HOMOZYGOUS_ALT));
		Assert.assertFalse(pedigree.singleSampleHasHeterozygousVariant(varList));
		// homozygous alt, homozygous ref -- no heterozygous variant
		varList.add(constructVariant(Genotype.HOMOZYGOUS_REF));
		Assert.assertFalse(pedigree.singleSampleHasHeterozygousVariant(varList));
		// homozygous alt, homozygous ref, heterozygous -- no heterozygous
		// variant
		varList.add(constructVariant(Genotype.HETEROZYGOUS));
		Assert.assertTrue(pedigree.singleSampleHasHeterozygousVariant(varList));
	}

	@Test
	public void testSingleSampleCompatibleWithAutosomalRecessive_homozygous() {
		Pedigree pedigree = Pedigree.constructSingleSamplePedigree("PERSON");
		ArrayList<Variant> varList = new ArrayList<Variant>();
		varList.add(constructVariant(Genotype.HOMOZYGOUS_ALT));
		Assert.assertTrue(pedigree.singleSampleCompatibleWithAutosomalRecessive(varList));
	}

	@Test
	public void testSingleSampleCompatibleWithAutosomalRecessive_heterozygous() {
		Pedigree pedigree = Pedigree.constructSingleSamplePedigree("PERSON");
		ArrayList<Variant> varList = new ArrayList<Variant>();
		varList.add(constructVariant(Genotype.HETEROZYGOUS));
		Assert.assertFalse(pedigree.singleSampleCompatibleWithAutosomalRecessive(varList));
		varList.add(constructVariant(Genotype.HETEROZYGOUS));
		Assert.assertTrue(pedigree.singleSampleCompatibleWithAutosomalRecessive(varList));
	}

	@Test
	public void testIsCompatibleWithXChromosomalRecessive_singleSample() {
		Pedigree pedigree = Pedigree.constructSingleSamplePedigree("PERSON");
		ArrayList<Variant> varList = new ArrayList<Variant>();
		varList.add(constructVariant(Genotype.HETEROZYGOUS));
		varList.get(0).setChromosome(refDict.contigID.get("X"));
		Assert.assertFalse(pedigree.isCompatibleWithXChromosomalRecessive(varList));
		varList.add(constructVariant(Genotype.HOMOZYGOUS_ALT));
		varList.get(1).setChromosome(refDict.contigID.get("X"));
		Assert.assertTrue(pedigree.isCompatibleWithXChromosomalRecessive(varList));
	}

	@Test
	public void testIsCompatibleWithXChromosomalRecessive_multiSample() throws PedParseException {
		Pedigree pedigree = new Pedigree(this.familyOfFour, "FAMILY");
		ArrayList<Variant> varList = new ArrayList<Variant>();
		varList.add(constructVariant(Genotype.HETEROZYGOUS, Genotype.HETEROZYGOUS, Genotype.HETEROZYGOUS,
				Genotype.HETEROZYGOUS));
		varList.get(0).setChromosome(refDict.contigID.get("X"));
		Assert.assertFalse(pedigree.isCompatibleWithXChromosomalRecessive(varList));
		varList.add(constructVariant(Genotype.HETEROZYGOUS, Genotype.HETEROZYGOUS, Genotype.HETEROZYGOUS,
				Genotype.HOMOZYGOUS_ALT));
		varList.get(1).setChromosome(refDict.contigID.get("X"));
		Assert.assertTrue(pedigree.isCompatibleWithXChromosomalRecessive(varList));
	}
}
