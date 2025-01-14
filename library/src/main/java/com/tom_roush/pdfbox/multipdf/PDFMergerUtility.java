/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tom_roush.pdfbox.multipdf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSInteger;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSNumber;
import com.tom_roush.pdfbox.cos.COSStream;
import com.tom_roush.pdfbox.cos.COSString;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentCatalog;
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDestinationDictionary;
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDictionary;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.PageMode;
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata;
import com.tom_roush.pdfbox.pdmodel.common.PDNumberTreeNode;
import com.tom_roush.pdfbox.pdmodel.common.PDStream;
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import com.tom_roush.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField;

/**
 * This class will take a list of pdf documents and merge them, saving the
 * result in a new document.
 *
 * @author Ben Litchfield
 */
public class PDFMergerUtility
{
    private static final String STRUCTURETYPE_DOCUMENT = "Document";

    private final List<InputStream> sources;
    private final List<FileInputStream> fileInputStreams;
    private String destinationFileName;
    private OutputStream destinationStream;
    private boolean ignoreAcroFormErrors = false;
    private PDDocumentInformation destinationDocumentInformation = null;
    private PDMetadata destinationMetadata = null;

    /**
     * Instantiate a new PDFMergerUtility.
     */
    public PDFMergerUtility()
    {
        sources = new ArrayList<InputStream>();
        fileInputStreams = new ArrayList<FileInputStream>();
    }

    /**
     * Get the name of the destination file.
     *
     * @return Returns the destination.
     */
    public String getDestinationFileName()
    {
        return destinationFileName;
    }

    /**
     * Set the name of the destination file.
     *
     * @param destination The destination to set.
     */
    public void setDestinationFileName(String destination)
    {
        destinationFileName = destination;
    }

    /**
     * Get the destination OutputStream.
     *
     * @return Returns the destination OutputStream.
     */
    public OutputStream getDestinationStream()
    {
        return destinationStream;
    }

    /**
     * Set the destination OutputStream.
     *
     * @param destStream The destination to set.
     */
    public void setDestinationStream(OutputStream destStream)
    {
        destinationStream = destStream;
    }

    /**
     * Get the destination document information that is to be set in {@link #mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting)
     * }. The default is null, which means that it is ignored.
     *
     * @return The destination document information.
     */
    public PDDocumentInformation getDestinationDocumentInformation()
    {
        return destinationDocumentInformation;
    }

    /**
     * Set the destination document information that is to be set in {@link #mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting)
     * }. The default is null, which means that it is ignored.
     *
     * @param info The destination document information.
     */
    public void setDestinationDocumentInformation(PDDocumentInformation info)
    {
        destinationDocumentInformation = info;
    }

    /**
     * Set the destination metadata that is to be set in {@link #mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting)
     * }. The default is null, which means that it is ignored.
     *
     * @return The destination metadata.
     */
    public PDMetadata getDestinationMetadata()
    {
        return destinationMetadata;
    }

    /**
     * Set the destination metadata that is to be set in {@link #mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting)
     * }. The default is null, which means that it is ignored.
     *
     * @param meta The destination metadata.
     */
    public void setDestinationMetadata(PDMetadata meta)
    {
        destinationMetadata = meta;
    }

    /**
     * Add a source file to the list of files to merge.
     *
     * @param source Full path and file name of source document.
     *
     * @throws FileNotFoundException If the file doesn't exist
     */
    public void addSource(String source) throws FileNotFoundException
    {
        addSource(new File(source));
    }

    /**
     * Add a source file to the list of files to merge.
     *
     * @param source File representing source document
     *
     * @throws FileNotFoundException If the file doesn't exist
     */
    public void addSource(File source) throws FileNotFoundException
    {
        FileInputStream stream = new FileInputStream(source);
        sources.add(stream);
        fileInputStreams.add(stream);
    }

    /**
     * Add a source to the list of documents to merge.
     *
     * @param source InputStream representing source document
     */
    public void addSource(InputStream source)
    {
        sources.add(source);
    }

    /**
     * Add a list of sources to the list of documents to merge.
     *
     * @param sourcesList List of InputStream objects representing source
     * documents
     */
    public void addSources(List<InputStream> sourcesList)
    {
        sources.addAll(sourcesList);
    }

    /**
     * Merge the list of source documents, saving the result in the destination file.
     *
     * @throws IOException If there is an error saving the document.
     * @deprecated use {@link #mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting) }
     */
    @Deprecated
    public void mergeDocuments() throws IOException
    {
        mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
    }

    /**
     * Merge the list of source documents, saving the result in the destination
     * file.
     *
     * @param memUsageSetting defines how memory is used for buffering PDF streams;
     *                        in case of <code>null</code> unrestricted main memory is used
     *
     * @throws IOException If there is an error saving the document.
     */
    public void mergeDocuments(MemoryUsageSetting memUsageSetting) throws IOException
    {
        PDDocument destination = null;
        InputStream sourceFile;
        PDDocument source;
        if (sources != null && sources.size() > 0)
        {
            List<PDDocument> tobeclosed = new ArrayList<PDDocument>();

            try
            {
                MemoryUsageSetting partitionedMemSetting = memUsageSetting != null ?
                    memUsageSetting.getPartitionedCopy(sources.size()+1) :
                    MemoryUsageSetting.setupMainMemoryOnly();
                Iterator<InputStream> sit = sources.iterator();
                destination = new PDDocument(partitionedMemSetting);

                while (sit.hasNext())
                {
                    sourceFile = sit.next();
                    source = PDDocument.load(sourceFile, partitionedMemSetting);
                    tobeclosed.add(source);
                    appendDocument(destination, source);
                }

                // optionally set meta data
                if (destinationDocumentInformation != null)
                {
                    destination.setDocumentInformation(destinationDocumentInformation);
                }
                if (destinationMetadata != null)
                {
                    destination.getDocumentCatalog().setMetadata(destinationMetadata);
                }

                if (destinationStream == null)
                {
                    destination.save(destinationFileName);
                }
                else
                {
                    destination.save(destinationStream);
                }
            }
            finally
            {
                if (destination != null)
                {
                    destination.close();
                }
                for (PDDocument doc : tobeclosed)
                {
                    doc.close();
                }
                for (FileInputStream stream : fileInputStreams)
                {
                    stream.close();
                }
            }
        }
    }

    /**
     * append all pages from source to destination.
     *
     * @param destination the document to receive the pages
     * @param source the document originating the new pages
     *
     * @throws IOException If there is an error accessing data from either
     * document.
     */
    public void appendDocument(PDDocument destination, PDDocument source) throws IOException
    {
        if (source.getDocument().isClosed())
        {
            throw new IOException("Error: source PDF is closed.");
        }
        if (destination.getDocument().isClosed())
        {
            throw new IOException("Error: destination PDF is closed.");
        }

        PDDocumentCatalog destCatalog = destination.getDocumentCatalog();
        PDDocumentCatalog srcCatalog = source.getDocumentCatalog();

        if (isDynamicXfa(srcCatalog.getAcroForm()))
        {
            throw new IOException("Error: can't merge source document containing dynamic XFA form content.");
        }

        PDDocumentInformation destInfo = destination.getDocumentInformation();
        PDDocumentInformation srcInfo = source.getDocumentInformation();
        destInfo.getCOSObject().mergeInto(srcInfo.getCOSObject());



        // use the highest version number for the resulting pdf
        float destVersion = destination.getVersion();
        float srcVersion = source.getVersion();

        if (destVersion < srcVersion)
        {
            destination.setVersion(srcVersion);
        }

        if (destCatalog.getOpenAction() == null)
        {
            destCatalog.setOpenAction(srcCatalog.getOpenAction());
        }

        PDFCloneUtility cloner = new PDFCloneUtility(destination);

        try
        {
            PDAcroForm destAcroForm = destCatalog.getAcroForm();
            PDAcroForm srcAcroForm = srcCatalog.getAcroForm();

            if (destAcroForm == null && srcAcroForm != null)
            {
                destCatalog.getCOSObject().setItem(COSName.ACRO_FORM,
                    cloner.cloneForNewDocument(srcAcroForm.getCOSObject()));

            }
            else
            {
                if (srcAcroForm != null)
                {
                    mergeAcroForm(cloner, destAcroForm, srcAcroForm);
                }
            }
        }
        catch (IOException e)
        {
            // if we are not ignoring exceptions, we'll re-throw this
            if (!ignoreAcroFormErrors)
            {
                throw new IOException(e);
            }
        }

        COSArray destThreads = (COSArray) destCatalog.getCOSObject().getDictionaryObject(COSName.THREADS);
        COSArray srcThreads = (COSArray) cloner.cloneForNewDocument(destCatalog.getCOSObject().getDictionaryObject(
            COSName.THREADS));
        if (destThreads == null)
        {
            destCatalog.getCOSObject().setItem(COSName.THREADS, srcThreads);
        }
        else
        {
            destThreads.addAll(srcThreads);
        }

        PDDocumentNameDictionary destNames = destCatalog.getNames();
        PDDocumentNameDictionary srcNames = srcCatalog.getNames();
        if (srcNames != null)
        {
            if (destNames == null)
            {
                destCatalog.getCOSObject().setItem(COSName.NAMES, cloner.cloneForNewDocument(srcNames));
            }
            else
            {
                cloner.cloneMerge(srcNames, destNames);
            }
        }

        PDDocumentNameDestinationDictionary destDests = destCatalog.getDests();
        PDDocumentNameDestinationDictionary srcDests = srcCatalog.getDests();
        if (srcDests != null)
        {
            if (destDests == null)
            {
                destCatalog.getCOSObject().setItem(COSName.DESTS, cloner.cloneForNewDocument(srcDests));
            }
            else
            {
                cloner.cloneMerge(srcDests, destDests);
            }
        }

        PDDocumentOutline destOutline = destCatalog.getDocumentOutline();
        PDDocumentOutline srcOutline = srcCatalog.getDocumentOutline();
        if (srcOutline != null)
        {
            if (destOutline == null)
            {
                PDDocumentOutline cloned = new PDDocumentOutline((COSDictionary) cloner.cloneForNewDocument(srcOutline));
                destCatalog.setDocumentOutline(cloned);
            }
            else
            {
                for (PDOutlineItem item : srcOutline.children())
                {
                    // get each child, clone its dictionary, remove siblings info,
                    // append outline item created from there
                    COSDictionary clonedDict = (COSDictionary) cloner.cloneForNewDocument(item);
                    clonedDict.removeItem(COSName.PREV);
                    clonedDict.removeItem(COSName.NEXT);
                    PDOutlineItem clonedItem = new PDOutlineItem(clonedDict);
                    destOutline.addLast(clonedItem);
                }
            }
        }

        PageMode destPageMode = destCatalog.getPageMode();
        PageMode srcPageMode = srcCatalog.getPageMode();
        if (destPageMode == null)
        {
            destCatalog.setPageMode(srcPageMode);
        }

        COSDictionary destLabels = (COSDictionary) destCatalog.getCOSObject().getDictionaryObject(
            COSName.PAGE_LABELS);
        COSDictionary srcLabels = (COSDictionary) srcCatalog.getCOSObject()
            .getDictionaryObject(COSName.PAGE_LABELS);
        if (srcLabels != null)
        {
            int destPageCount = destination.getNumberOfPages();
            COSArray destNums;
            if (destLabels == null)
            {
                destLabels = new COSDictionary();
                destNums = new COSArray();
                destLabels.setItem(COSName.NUMS, destNums);
                destCatalog.getCOSObject().setItem(COSName.PAGE_LABELS, destLabels);
            }
            else
            {
                destNums = (COSArray) destLabels.getDictionaryObject(COSName.NUMS);
            }
            COSArray srcNums = (COSArray) srcLabels.getDictionaryObject(COSName.NUMS);
            if (srcNums != null)
            {
                for (int i = 0; i < srcNums.size(); i += 2)
                {
                    COSNumber labelIndex = (COSNumber) srcNums.getObject(i);
                    long labelIndexValue = labelIndex.intValue();
                    destNums.add(COSInteger.get(labelIndexValue + destPageCount));
                    destNums.add(cloner.cloneForNewDocument(srcNums.getObject(i + 1)));
                }
            }
        }

        COSStream destMetadata = (COSStream) destCatalog.getCOSObject().getDictionaryObject(COSName.METADATA);
        COSStream srcMetadata = (COSStream) srcCatalog.getCOSObject().getDictionaryObject(COSName.METADATA);
        if (destMetadata == null && srcMetadata != null)
        {
            PDStream newStream = new PDStream(destination, srcMetadata.createInputStream(), (COSName) null);
            newStream.getCOSObject().mergeInto(srcMetadata);
            destCatalog.getCOSObject().setItem(COSName.METADATA, newStream);
        }

        mergeOutputIntents(cloner, srcCatalog, destCatalog);

        // merge logical structure hierarchy if logical structure information is available in both source pdf and
        // destination pdf
        boolean mergeStructTree = false;
        int destParentTreeNextKey = -1;
        COSDictionary destParentTreeDict = null;
        COSDictionary srcParentTreeDict;
        COSArray destNumbersArray = null;
        COSArray srcNumbersArray = null;
        PDMarkInfo destMark = destCatalog.getMarkInfo();
        PDStructureTreeRoot destStructTree = destCatalog.getStructureTreeRoot();
        PDMarkInfo srcMark = srcCatalog.getMarkInfo();
        PDStructureTreeRoot srcStructTree = srcCatalog.getStructureTreeRoot();
        if (destStructTree != null)
        {
            PDNumberTreeNode destParentTree = destStructTree.getParentTree();
            destParentTreeNextKey = destStructTree.getParentTreeNextKey();
            if (destParentTree != null)
            {
                destParentTreeDict = destParentTree.getCOSObject();
                destNumbersArray = (COSArray) destParentTreeDict.getDictionaryObject(COSName.NUMS);
                if (destNumbersArray != null)
                {
                    if (destParentTreeNextKey < 0)
                    {
                        destParentTreeNextKey = destNumbersArray.size() / 2;
                    }
                    if (destParentTreeNextKey > 0 && srcStructTree != null)
                    {
                        PDNumberTreeNode srcParentTree = srcStructTree.getParentTree();
                        if (srcParentTree != null)
                        {
                            srcParentTreeDict = srcParentTree.getCOSObject();
                            srcNumbersArray = (COSArray) srcParentTreeDict.getDictionaryObject(COSName.NUMS);
                            if (srcNumbersArray != null)
                            {
                                mergeStructTree = true;
                            }
                        }
                    }
                }
            }
            if (destMark != null && destMark.isMarked() && !mergeStructTree)
            {
                destMark.setMarked(false);
            }
            if (!mergeStructTree)
            {
                destCatalog.setStructureTreeRoot(null);
            }
        }

        Map<COSDictionary, COSDictionary> objMapping = new HashMap<COSDictionary, COSDictionary>();
        for (PDPage page : srcCatalog.getPages())
        {
            PDPage newPage = new PDPage((COSDictionary) cloner.cloneForNewDocument(page.getCOSObject()));
            newPage.setCropBox(page.getCropBox());
            newPage.setMediaBox(page.getMediaBox());
            newPage.setRotation(page.getRotation());
            // this is smart enough to just create references for resources that are used on multiple pages
            newPage.setResources(new PDResources((COSDictionary) cloner.cloneForNewDocument(page.getResources())));
            if (mergeStructTree)
            {
                updateStructParentEntries(newPage, destParentTreeNextKey);
                objMapping.put(page.getCOSObject(), newPage.getCOSObject());
                List<PDAnnotation> oldAnnots = page.getAnnotations();
                List<PDAnnotation> newAnnots = newPage.getAnnotations();
                for (int i = 0; i < oldAnnots.size(); i++)
                {
                    objMapping.put(oldAnnots.get(i).getCOSObject(), newAnnots.get(i).getCOSObject());
                }
                // TODO update mapping for XObjects
            }
            destination.addPage(newPage);
        }
        if (mergeStructTree)
        {
            updatePageReferences(srcNumbersArray, objMapping);
            for (int i = 0; i < srcNumbersArray.size() / 2; i++)
            {
                destNumbersArray.add(COSInteger.get(destParentTreeNextKey + i));
                destNumbersArray.add(srcNumbersArray.getObject(i * 2 + 1));
            }
            destParentTreeNextKey += srcNumbersArray.size() / 2;
            destParentTreeDict.setItem(COSName.NUMS, destNumbersArray);
            PDNumberTreeNode newParentTreeNode = new PDNumberTreeNode(destParentTreeDict, COSBase.class);
            destStructTree.setParentTree(newParentTreeNode);
            destStructTree.setParentTreeNextKey(destParentTreeNextKey);

            COSDictionary kDictLevel0 = new COSDictionary();
            COSArray newKArray = new COSArray();
            COSArray destKArray = destStructTree.getKArray();
            COSArray srcKArray = srcStructTree.getKArray();
            if (destKArray != null && srcKArray != null)
            {
                updateParentEntry(destKArray, kDictLevel0);
                newKArray.addAll(destKArray);
                if (mergeStructTree)
                {
                    updateParentEntry(srcKArray, kDictLevel0);
                }
                newKArray.addAll(srcKArray);
            }
            kDictLevel0.setItem(COSName.K, newKArray);
            kDictLevel0.setItem(COSName.P, destStructTree);
            kDictLevel0.setItem(COSName.S, new COSString(STRUCTURETYPE_DOCUMENT));
            destStructTree.setK(kDictLevel0);
        }
    }

    // copy outputIntents to destination, but avoid duplicate OutputConditionIdentifier,
    // except when it is missing or is named "Custom".
    private void mergeOutputIntents(PDFCloneUtility cloner,
        PDDocumentCatalog srcCatalog, PDDocumentCatalog destCatalog) throws IOException
    {
        List<PDOutputIntent> srcOutputIntents = srcCatalog.getOutputIntents();
        List<PDOutputIntent> dstOutputIntents = destCatalog.getOutputIntents();
        for (PDOutputIntent srcOI : srcOutputIntents)
        {
            String srcOCI = srcOI.getOutputConditionIdentifier();
            if (srcOCI != null && !"Custom".equals(srcOCI))
            {
                // is that identifier already there?
                boolean skip = false;
                for (PDOutputIntent dstOI : dstOutputIntents)
                {
                    if (dstOI.getOutputConditionIdentifier().equals(srcOCI))
                    {
                        skip = true;
                        break;
                    }
                }
                if (skip)
                {
                    continue;
                }
            }
            destCatalog.addOutputIntent(new PDOutputIntent((COSDictionary) cloner.cloneForNewDocument(srcOI)));
            dstOutputIntents.add(srcOI);
        }
    }

    private int nextFieldNum = 1;

    /**
     * Merge the contents of the source form into the destination form for the
     * destination file.
     *
     * @param cloner the object cloner for the destination document
     * @param destAcroForm the destination form
     * @param srcAcroForm the source form
     * @throws IOException If an error occurs while adding the field.
     */
    private void mergeAcroForm(PDFCloneUtility cloner, PDAcroForm destAcroForm, PDAcroForm srcAcroForm)
        throws IOException
    {

        List<PDField> srcFields = srcAcroForm.getFields();

        if (srcFields != null)
        {
            // if a form is merged multiple times using PDFBox the newly generated
            // fields starting with dummyFieldName may already exist. We need to determine the last unique
            // number used and increment that.
            final String prefix = "dummyFieldName";
            final int prefixLength = prefix.length();

            for (PDField destField : destAcroForm.getFieldTree())
            {
                String fieldName = destField.getPartialName();
                if (fieldName.startsWith(prefix))
                {
                    nextFieldNum = Math.max(nextFieldNum, Integer.parseInt(fieldName.substring(prefixLength, fieldName.length()))+1);
                }
            }

            COSArray destFields = (COSArray) destAcroForm.getCOSObject().getItem(COSName.FIELDS);
            for (PDField srcField : srcAcroForm.getFieldTree())
            {
                COSDictionary dstField = (COSDictionary) cloner.cloneForNewDocument(srcField.getCOSObject());
                // if the form already has a field with this name then we need to rename this field
                // to prevent merge conflicts.
                if (destAcroForm.getField(srcField.getFullyQualifiedName()) != null)
                {
                    dstField.setString(COSName.T, prefix + nextFieldNum++);
                }
                destFields.add(dstField);
            }
            destAcroForm.getCOSObject().setItem(COSName.FIELDS,destFields);
        }
    }

    /**
     * Indicates if acroform errors are ignored or not.
     *
     * @return true if acroform errors are ignored
     */
    public boolean isIgnoreAcroFormErrors()
    {
        return ignoreAcroFormErrors;
    }

    /**
     * Set to true to ignore acroform errors.
     *
     * @param ignoreAcroFormErrorsValue true if acroform errors should be
     * ignored
     */
    public void setIgnoreAcroFormErrors(boolean ignoreAcroFormErrorsValue)
    {
        ignoreAcroFormErrors = ignoreAcroFormErrorsValue;
    }

    /**
     * Update the Pg and Obj references to the new (merged) page.
     *
     * @param parentTreeEntry
     * @param objMapping mapping between old and new references
     */
    private void updatePageReferences(COSDictionary parentTreeEntry, Map<COSDictionary, COSDictionary> objMapping)
    {
        COSBase page = parentTreeEntry.getDictionaryObject(COSName.PG);
        if (page instanceof COSDictionary && objMapping.containsKey(page))
        {
            parentTreeEntry.setItem(COSName.PG, objMapping.get(page));
        }
        COSBase obj = parentTreeEntry.getDictionaryObject(COSName.OBJ);
        if (obj instanceof COSDictionary && objMapping.containsKey(obj))
        {
            parentTreeEntry.setItem(COSName.OBJ, objMapping.get(obj));
        }
        COSBase kSubEntry = parentTreeEntry.getDictionaryObject(COSName.K);
        if (kSubEntry instanceof COSArray)
        {
            updatePageReferences((COSArray) kSubEntry, objMapping);
        }
        else if (kSubEntry instanceof COSDictionary)
        {
            updatePageReferences((COSDictionary) kSubEntry, objMapping);
        }
    }

    private void updatePageReferences(COSArray parentTreeEntry, Map<COSDictionary, COSDictionary> objMapping)
    {
        for (int i = 0; i < parentTreeEntry.size(); i++)
        {
            COSBase subEntry = parentTreeEntry.getObject(i);
            if (subEntry instanceof COSArray)
            {
                updatePageReferences((COSArray) subEntry, objMapping);
            }
            else if (subEntry instanceof COSDictionary)
            {
                updatePageReferences((COSDictionary) subEntry, objMapping);
            }
        }
    }

    /**
     * Update the P reference to the new parent dictionary.
     *
     * @param kArray the kids array
     * @param newParent the new parent
     */
    private void updateParentEntry(COSArray kArray, COSDictionary newParent)
    {
        for (int i = 0; i < kArray.size(); i++)
        {
            COSBase subEntry = kArray.getObject(i);
            if (subEntry instanceof COSDictionary)
            {
                COSDictionary dictEntry = (COSDictionary) subEntry;
                if (dictEntry.getDictionaryObject(COSName.P) != null)
                {
                    dictEntry.setItem(COSName.P, newParent);
                }
            }
        }
    }

    /**
     * Update the StructParents and StructParent values in a PDPage.
     *
     * @param page the new page
     * @param structParentOffset the offset which should be applied
     */
    private void updateStructParentEntries(PDPage page, int structParentOffset) throws IOException
    {
        page.setStructParents(page.getStructParents() + structParentOffset);
        List<PDAnnotation> annots = page.getAnnotations();
        List<PDAnnotation> newannots = new ArrayList<PDAnnotation>();
        for (PDAnnotation annot : annots)
        {
            annot.setStructParent(annot.getStructParent() + structParentOffset);
            newannots.add(annot);
        }
        page.setAnnotations(newannots);
    }

    /**
     * Test for dynamic XFA content.
     *
     * @param acroForm the AcroForm
     * @return true if there is a dynamic XFA form.
     */
    private boolean isDynamicXfa(PDAcroForm acroForm)
    {
        return acroForm != null && acroForm.xfaIsDynamic();
    }
}
