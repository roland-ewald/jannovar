package de.charite.compbio.jannovar.annotation.builders;

import java.util.ArrayList;

import com.google.common.collect.ImmutableList;

import de.charite.compbio.jannovar.annotation.Annotation;
import de.charite.compbio.jannovar.annotation.InvalidGenomeChange;
import de.charite.compbio.jannovar.annotation.VariantEffect;
import de.charite.compbio.jannovar.impl.util.StringUtil;
import de.charite.compbio.jannovar.impl.util.Translator;
import de.charite.compbio.jannovar.reference.AminoAcidChange;
import de.charite.compbio.jannovar.reference.AminoAcidChangeNormalizer;
import de.charite.compbio.jannovar.reference.CDSPosition;
import de.charite.compbio.jannovar.reference.GenomeInterval;
import de.charite.compbio.jannovar.reference.GenomePosition;
import de.charite.compbio.jannovar.reference.GenomeVariant;
import de.charite.compbio.jannovar.reference.TranscriptModel;

// TODO(holtgrem): The block substitution protein annotation generation needs some love in the corner cases.

/**
 * Builds {@link Annotation} objects for the block substitution {@link GenomeVariant} in the given {@link TranscriptInfo}
 * .
 *
 * @author Manuel Holtgrewe <manuel.holtgrewe@charite.de>
 */
public final class BlockSubstitutionAnnotationBuilder extends AnnotationBuilder {

	/**
	 * @param transcript
	 *            {@link TranscriptInfo} to build the annotation for
	 * @param change
	 *            {@link GenomeVariant} to build the annotation with
	 * @param options
	 *            the configuration to use for the {@link AnnotationBuilder}
	 * @throws InvalidGenomeChange
	 *             if <code>change</code> did not describe a block substitution
	 */
	public BlockSubstitutionAnnotationBuilder(TranscriptModel transcript, GenomeVariant change, AnnotationBuilderOptions options)
			throws InvalidGenomeChange {
		super(transcript, change, options);

		// Guard against invalid genome change.
		if (change.getRef().length() == 0 || change.getAlt().length() == 0)
			throw new InvalidGenomeChange("GenomeChange " + change + " does not describe a block substitution.");
	}

	@Override
	public Annotation build() {
		// Go through top-level cases (clustered by how they are handled here) and build annotations for each of them
		// where applicable.

		if (!transcript.isCoding())
			return buildNonCodingAnnotation();

		final GenomeInterval changeInterval = change.getGenomeInterval();
		if (so.containsExon(changeInterval)) // deletion of whole exon
			return buildFeatureAblationAnnotation();
		else if (so.overlapsWithTranslationalStartSite(changeInterval))
			return buildStartLossAnnotation();
		else if (so.overlapsWithCDSExon(changeInterval) && so.overlapsWithCDS(changeInterval))
			return new CDSExonicAnnotationBuilder().build(); // can affect amino acids
		else if (so.overlapsWithCDSIntron(changeInterval) && so.overlapsWithCDS(changeInterval))
			return buildIntronicAnnotation(); // intron but no exon => intronic variant
		else if (so.overlapsWithFivePrimeUTR(changeInterval) || so.overlapsWithThreePrimeUTR(changeInterval))
			return buildUTRAnnotation();
		else if (so.overlapsWithUpstreamRegion(changeInterval) || so.overlapsWithDownstreamRegion(changeInterval))
			return buildUpOrDownstreamAnnotation();
		else
			return buildIntergenicAnnotation();
	}

	@Override
	protected String ncHGVS() {
		return StringUtil.concatenate(dnaAnno, "delins", change.getAlt());
	}

	private Annotation buildFeatureAblationAnnotation() {
		return new Annotation(transcript, change, ImmutableList.of(VariantEffect.TRANSCRIPT_ABLATION), locAnno, ncHGVS(),
				null);
	}

	private Annotation buildStartLossAnnotation() {
		return new Annotation(transcript, change, ImmutableList.of(VariantEffect.START_LOST), locAnno, ncHGVS(), "p.0?");
	}

	/**
	 * Helper class for generating annotations for exonic CDS variants.
	 *
	 * We use this helper class to simplify the access to the parameters such as {@link #wtCDSSeq} etc.
	 */
	private class CDSExonicAnnotationBuilder {
		final GenomeInterval changeInterval;

		final Translator t = Translator.getTranslator();

		final String wtCDSSeq;
		final String varCDSSeq;
		final int delFrameShift;

		final String wtAASeq;
		final String varAASeq;
		final int varAAStopPos;

		// TODO(holtgrem): Fix "value not used" variable warning by removing?
		final CDSPosition refChangeBeginPos;
		@SuppressWarnings("unused")
		final CDSPosition refChangeLastPos;
		final CDSPosition varChangeBeginPos;
		@SuppressWarnings("unused")
		final CDSPosition varChangeLastPos;

		// We keep the following three variables as state of the algorithm since we do not have easy-to-use triples in
		// Java.

		// the variant types, updated in handleFrameShiftCase() and handleNonFrameShiftCase()
		ArrayList<VariantEffect> varTypes = new ArrayList<VariantEffect>();
		// the amino acid change, updated in handleFrameShiftCase() and handleNonFrameShiftCase()
		AminoAcidChange aaChange;
		// the protein annotation, updated in handleFrameShiftCase() and handleNonFrameShiftCase()
		String protAnno;

		public CDSExonicAnnotationBuilder() {
			this.changeInterval = change.getGenomeInterval();
			this.wtCDSSeq = projector.getTranscriptStartingAtCDS();
			this.varCDSSeq = seqChangeHelper.getCDSWithGenomeVariant(change);
			this.delFrameShift = (varCDSSeq.length() - wtCDSSeq.length()) % 3;

			// Translate the variant CDS sequence.
			this.wtAASeq = t.translateDNA(wtCDSSeq);
			this.varAASeq = t.translateDNA(varCDSSeq);

			// Get the reference change begin position as CDS coordinate, handling introns and positions outside of CDS.
			this.refChangeBeginPos = projector.projectGenomeToCDSPosition(changeInterval.getGenomeBeginPos());
			GenomePosition refChangeLastGenomePos = changeInterval.getGenomeEndPos().shifted(-1);
			CDSPosition refChangeLastPos = projector.projectGenomeToCDSPosition(refChangeLastGenomePos);
			// shift if end lies in intro or was project to end position
			if (so.liesInCDSIntron(refChangeLastGenomePos)
					|| !transcript.getCDSRegion().contains(changeInterval.getGenomeEndPos().shifted(-1)))
				refChangeLastPos = refChangeLastPos.shifted(-1);
			this.refChangeLastPos = refChangeLastPos;
			// Get the variant change begin position as CDS coordinate, handling introns and positions outside of CDS.
			this.varChangeBeginPos = projector.projectGenomeToCDSPosition(changeInterval.getGenomeBeginPos());
			CDSPosition varChangeLastPos = projector.projectGenomeToCDSPosition(changeInterval.getGenomeBeginPos()
					.shifted(change.getAlt().length() - 1));
			if (!transcript.getCDSRegion().contains(changeInterval.getGenomeEndPos().shifted(-1)))
				varChangeLastPos = varChangeLastPos.shifted(-1); // shift if projected to end position
			this.varChangeLastPos = varChangeLastPos;
			// "(...+2)/3" => round up integer division result
			this.aaChange = new AminoAcidChange(refChangeBeginPos.getPos() / 3, wtAASeq.substring(refChangeBeginPos.getPos() / 3,
					(refChangeLastPos.getPos() + 1 + 2) / 3), varAASeq.substring(varChangeBeginPos.getPos() / 3,
							(varChangeLastPos.getPos() + 1 + 2) / 3));

			// Look for stop codon, starting at change position.
			this.varAAStopPos = varAASeq.indexOf('*', refChangeBeginPos.getPos() / 3);
		}

		public Annotation build() {
			if (delFrameShift == 0)
				handleNonFrameShiftCase();
			else
				handleFrameShiftCase();

			return new Annotation(transcript, change, varTypes, locAnno, ncHGVS(), protAnno);
		}

		private void handleNonFrameShiftCase() {
			// The variant is a non-frameshift block substitution. The substitution could span more than one exon and
			// thus also affect a splice donor or acceptor site, delete a stop codon. Further, the variant might also be
			// a splice region variant but that a lower priority than a non-frameshift deletion.
			//
			// Check for being a splice site variant. The splice donor, acceptor, and region intervals are disjoint.
			if (so.overlapsWithSpliceDonorSite(changeInterval))
				varTypes.addAll(ImmutableList.of(VariantEffect.SPLICE_DONOR_VARIANT));
			else if (so.overlapsWithSpliceAcceptorSite(changeInterval))
				varTypes.addAll(ImmutableList.of(VariantEffect.SPLICE_ACCEPTOR_VARIANT));
			else if (so.overlapsWithSpliceRegion(changeInterval))
				varTypes.addAll(ImmutableList.of(VariantEffect.SPLICE_REGION_VARIANT));
			if (so.overlapsWithTranslationalStopSite(changeInterval))
				varTypes.add(VariantEffect.STOP_LOST);
			else if (change.getAlt().length() > change.getRef().length())
				varTypes.addAll(ImmutableList.of(VariantEffect.INTERNAL_FEATURE_ELONGATION));
			else if (change.getAlt().length() < change.getRef().length())
				varTypes.addAll(ImmutableList.of(VariantEffect.FEATURE_TRUNCATION, VariantEffect.COMPLEX_SUBSTITUTION));
			else
				varTypes.add(VariantEffect.MNV);

			if (refChangeBeginPos.getFrameshift() == 0) {
				// TODO(holtgrem): Implement shifting for substitutions for AA string
			}

			// Normalize the amino acid change with the var AA sequence.
			while (aaChange.getRef().length() > 0 && aaChange.getAlt().length() > 0
					&& aaChange.getRef().charAt(0) == aaChange.getAlt().charAt(0))
				aaChange = aaChange.shiftRight();
			// Truncate change.alt after stop codon.
			aaChange = AminoAcidChangeNormalizer.truncateAltAfterStopCodon(aaChange);
			if (aaChange.getAlt().equals("*"))
				varTypes.add(VariantEffect.STOP_GAINED);

			// Handle the case of no change.
			if (aaChange.isNop()) {
				protAnno = "p.=";
				return;
			}

			char wtAAFirst = wtAASeq.charAt(aaChange.getPos());
			char wtAALast = wtAASeq.charAt(aaChange.getLastPos());
			String insertedAAs = varAASeq.substring(aaChange.getPos(), aaChange.getPos() + aaChange.getAlt().length());
			String wtAAFirstLong = (wtAAFirst == '*') ? "*" : t.toLong(wtAAFirst);
			String wtAALastLong = (wtAALast == '*') ? "*" : t.toLong(wtAALast);

			// We differentiate the case of replacing a single amino acid and replacing multiple ones. Note that
			// when the result starts with the stop codon (the alt truncation step reduces it to the case of
			// "any>*") then we handle it as replacing the first amino acid by the stop codon.
			if (insertedAAs.isEmpty() && aaChange.getRef().length() > 1)
				protAnno = StringUtil.concatenate("p.", wtAAFirstLong, aaChange.getPos() + 1, "_", wtAALastLong,
						aaChange.getLastPos() + 1, "del");
			if (insertedAAs.isEmpty() && aaChange.getRef().length() == 1)
				protAnno = StringUtil.concatenate("p.", wtAAFirstLong, aaChange.getPos() + 1, "del");
			else if (aaChange.getPos() == aaChange.getLastPos() || aaChange.getAlt().equals("*"))
				protAnno = StringUtil.concatenate("p.", wtAAFirstLong, aaChange.getPos() + 1,
						t.toLong(insertedAAs.charAt(0)));
			else
				protAnno = StringUtil.concatenate("p.", wtAAFirstLong, aaChange.getPos() + 1, "_", wtAALastLong,
						aaChange.getLastPos() + 1, "delins", t.toLong(insertedAAs));

			// In the case of stop loss, we have to add the "ext" suffix to the protein annotation.
			if (so.overlapsWithTranslationalStopSite(changeInterval)) {
				// Differentiate between the variant AA string containing a stop codon or not.
				if (varAAStopPos >= 0)
					protAnno = StringUtil.concatenate(protAnno, "ext*", varAAStopPos - aaChange.getPos() + 1);
				else
					protAnno = StringUtil.concatenate(protAnno, "ext*?");
			}

			if (!varTypes.contains(VariantEffect.MNV))
				varTypes.add(VariantEffect.COMPLEX_SUBSTITUTION);
		}

		private void handleFrameShiftCase() {
			// The variant is a frameshift deletion. The deletion could span more than one exon and thus also affect a
			// splice donor or acceptor site. Further, the variant might also be stop lost or splice region variant but
			// that has a lower priority than a frameshift deletion.
			//
			// Check for being a splice site variant. The splice donor, acceptor, and region intervals are disjoint.
			if (so.overlapsWithSpliceDonorSite(changeInterval))
				varTypes.addAll(ImmutableList.of(VariantEffect.SPLICE_DONOR_VARIANT));
			else if (so.overlapsWithSpliceAcceptorSite(changeInterval))
				varTypes.addAll(ImmutableList.of(VariantEffect.SPLICE_ACCEPTOR_VARIANT));
			else if (so.overlapsWithSpliceRegion(changeInterval))
				varTypes.addAll(ImmutableList.of(VariantEffect.SPLICE_REGION_VARIANT));
			// Check whether it overlaps with the stop site.
			if (so.overlapsWithTranslationalStopSite(changeInterval))
				varTypes.add(VariantEffect.STOP_LOST);

			// Differentiate between the cases where we have a stop codon and those where we don't.
			if (varAAStopPos >= 0) {
				// We have a stop codon, yay!
				char wtAA = wtAASeq.charAt(aaChange.getPos());
				char varAA = varAASeq.charAt(aaChange.getPos());
				int stopCodonOffset = varAAStopPos - aaChange.getPos() + 1;
				String wtAALong = (wtAA == '*') ? "*" : t.toLong(wtAA);
				String varAALong = (varAA == '*') ? "*" : t.toLong(varAA);
				String opDesc = "fs"; // operation description
				if (aaChange.getRef().indexOf('*') >= 0)
					opDesc = "ext"; // stop codon deleted
				protAnno = StringUtil.concatenate("p.", wtAALong, aaChange.getPos() + 1, varAALong, opDesc, "*",
						stopCodonOffset);
				if (varAALong.length() > varAALong.length())
					varTypes.add(VariantEffect.FRAMESHIFT_ELONGATION);
				else if (varAALong.length() < varAALong.length())
					varTypes.add(VariantEffect.FRAMESHIFT_TRUNCATION);
				else if (varAALong.length() > varAALong.length())
					varTypes.add(VariantEffect.FRAMESHIFT_VARIANT);
			} else {
				// There is no stop codon any more! Create a "probably no protein is produced".
				protAnno = "p.0?";

				varTypes.addAll(ImmutableList.of(VariantEffect.FRAMESHIFT_VARIANT, VariantEffect.STOP_LOST));
			}

			varTypes.add(VariantEffect.COMPLEX_SUBSTITUTION);
		}
	}

}
