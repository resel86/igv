/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.variant;

import com.mongodb.WriteResult;
import junit.framework.Assert;
import org.broad.igv.AbstractHeadlessTest;
import org.broad.igv.variant.vcf.VCFVariant;
import org.broadinstitute.sting.gatk.walkers.na12878kb.core.MongoVariantContext;
import org.broadinstitute.sting.gatk.walkers.na12878kb.core.NA12878DBArgumentCollection;
import org.broadinstitute.sting.gatk.walkers.na12878kb.core.NA12878KnowledgeBase;
import org.broadinstitute.sting.gatk.walkers.na12878kb.core.TruthStatus;
import org.broadinstitute.sting.utils.variantcontext.VariantContext;
import org.broadinstitute.sting.utils.variantcontext.VariantContextBuilder;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test retrieving variants from mongo database.
 * This test requires that the relevant mongo db be running,
 * so we ignore it because we can't be sure about foreign conditions.
 *
 * User: jacob
 * Date: 2012-Dec-14
 */
public class NA12878KBReviewTest extends AbstractHeadlessTest{

    String dbSpecPath = "resources/NA12878kb_local.json";

    private int allele0 = 0;
    private int allele1 = 1;
    private String callsetName = "test_callset";
    private TruthStatus truthStatus = TruthStatus.SUSPECT;

    private String chr = "chr10";
    //0-based coords
    private int start = (int) 1e6;
    private int end = start + 1;
    private List<String> alleles = Arrays.asList("A", "G");
    private MongoVariantContext mvc;

    private NA12878KBReviewSource source;

    @Before
    public void setUp() throws Exception{
        super.setUp();
        VariantContextBuilder builder = new VariantContextBuilder();
        //Convert from exclusive end to inclusive end
        builder.start(start + 1).stop(end).chr(chr).alleles(alleles);
        VariantContext vc = builder.make();
        mvc = NA12878KBReviewSource.createMVC(allele0, allele1, callsetName, vc, truthStatus);

        int errorsResetting = 0;
        try{
            errorsResetting = resetDB();
        }catch (Exception e){
            System.out.println(e);
            Assume.assumeNoException(e);
        }
        Assume.assumeTrue(errorsResetting == 0);

        source = new NA12878KBReviewSource(dbSpecPath);
    }

    private boolean checkFeatureNotPresent() throws Exception{
        Iterator<VCFVariant> result = getFeature();
        return !result.hasNext();
    }

    /*
     * Basically here to check the remove call
     */
    @Test
    public void testFeatureNotPresent() throws Exception{
        boolean featNotPresent = checkFeatureNotPresent();
        if(!featNotPresent){
            Iterator<VCFVariant> result = getFeature();
            while(result.hasNext()){
                System.out.println(result.next());
            }
        }
        assertTrue(featNotPresent);
    }

    //TODO Separate into add/get methods, but that requires prepopulation of data
    @Test
    public void testAddGetFeature() throws Exception{
        Assume.assumeTrue(checkFeatureNotPresent());

        String errorMessage = VariantReviewDialog.addCall(dbSpecPath, mvc);
        if(errorMessage != null) System.out.println(errorMessage);
        assertNull(errorMessage);

        Iterator<VCFVariant> result = getFeature();
        Assert.assertTrue("Empty result after adding call", result.hasNext());
        VCFVariant variant = result.next();
        Assert.assertFalse("Empty result after adding call", result.hasNext());

        assertEquals(chr, variant.getChr());
        assertEquals(start, variant.getStart());
        assertEquals(end, variant.getEnd());
        assertEquals(alleles.get(0), variant.getReference().replace("*", ""));
        int index = 1;
        for(Allele al: variant.getAlternateAlleles()){
            assertEquals(alleles.get(index++), al.toString());
        }
        assertTrue(variant.getSource().equals(callsetName));

    }

    private Iterator<VCFVariant> getFeature() throws IOException{
        return source.getFeatures(chr, start, end);
    }

    private int resetDB() throws Exception{
        NA12878DBArgumentCollection args = new NA12878DBArgumentCollection(dbSpecPath);
        NA12878KnowledgeBase kb = new NA12878KnowledgeBase(null, args);

        List<WriteResult> writeResults = kb.removeCall(mvc);
        kb.close();
        int errCount = 0;
        for(WriteResult wr: writeResults){
            if(wr.getError() != null){
                System.out.println(wr.getError());
                errCount++;
            }
        }
        return errCount;
    }
}
