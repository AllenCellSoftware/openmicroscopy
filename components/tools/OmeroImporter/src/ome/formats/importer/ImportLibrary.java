/*
 * ome.formats.testclient.ImportLibrary
 *
 *------------------------------------------------------------------------------
 *
 *  Copyright (C) 2005 Open Microscopy Environment
 *      Massachusetts Institute of Technology,
 *      National Institutes of Health,
 *      University of Dundee
 *
 *
 *------------------------------------------------------------------------------
 */

package ome.formats.importer;

import static omero.rtypes.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import loci.formats.FormatException;
import loci.common.DataTools;
import ome.formats.OMEROMetadataStore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ome.formats.importer.util.Actions;

import static omero.rtypes.*;
import omero.RBool;
import omero.RInt;
import omero.RString;
import omero.ServerError;
import omero.model.BooleanAnnotationI;
import omero.model.ColorI;
import omero.model.Dataset;
import omero.model.Image;
import omero.model.ImageI;
import omero.model.Pixels;

/**
 * support class for the proper usage of {@link OMEROMetadataStore} and
 * {@link FormatReader} instances. This library was factored out of
 * {@link ImportHandler} to support {@link ImportFixture} The general workflow
 * for this class (as seen in {@link ImportFixture} is: <code>
 *   ImportLibrary library = new ImportLibrary(store,reader,files);
 *   for (File file : files) {
 *     String fileName = file.getAbsolutePath();
 *     library.open(fileName);
 *     int count = library.calculateImageCount(fileName);
 *     long pixId = library.importMetadata();
 *     library.importData(pixId, fileName, new ImportLibrary.Step(){
 *       public void step(int i) {}});
 *   }
 * </code>
 * 
 * @author Josh Moore, josh.moore at gmx.de
 * @version $Revision: 1167 $, $Date: 2006-12-15 10:39:34 +0000 (Fri, 15 Dec 2006) $
 * @see FormatReader
 * @see OMEROMetadataStore
 * @see ImportHandler
 * @see ImportFixture
 * @since 3.0-M3
 */
public class ImportLibrary implements IObservable
{
   
    ArrayList<IObserver> observers = new ArrayList<IObserver>();
    
    /**
     * simple action class to be used during
     * {@link ImportLibrary#importData(long, String, ome.formats.testclient.ImportLibrary.Step)}
     */
    public abstract static class Step
    {

        public abstract void step(int series, int step);
    }

    private boolean dumpPixels = false;
    
    private static Log         log = LogFactory.getLog(ImportLibrary.class);

    private Dataset           dataset;

    private OMEROMetadataStore store;

    private OMEROWrapper       reader;

    private ImportContainer[]  fads;

    private int                sizeZ;

    private int                sizeT;

    private int                sizeC;
    
    private int                sizeX;
    
    private int                sizeY;

    private int                zSize;

    private int                tSize;

    private int                wSize;

    /**
     * Note: {@link #setDataset(String)} must be properly invoked before
     * {@link #importMetadata()} can be used.
     * 
     * @param store not null
     * @param reader not null
     * @param fads2 not null, length > 0
     */
    public ImportLibrary(OMEROMetadataStore store, OMEROWrapper reader)
    {
        this.store = store;
        this.reader = reader;

        if (store == null || reader == null)
        {
            // FIXME: Blitz transition, ApiUsageException no longer client side.
            throw new RuntimeException(
                    "All arguments to ImportLibrary() must be non-null.");
        }
    }

    /**
     * sets the dataset to which images will be imported. Must be called before
     * {@link #importMetadata()}
     * 
     * @param dataset2 Not null.
     */
    public void setDataset(Dataset dataset2)
    {
        if (dataset2 == null)
            // FIXME: Blitz transition, ApiUsageException no longer client side.
            throw new RuntimeException("Dataset name cannot be null.");
        this.dataset = dataset2;
    }

    
    // ~ Getters
    // =========================================================================

    /** simpler getter. Checks if dataset is still null */
    public Dataset getDataset()
    {
        if (this.dataset == null)
            // FIXME: Blitz transition, ApiUsageException no longer client side.
            throw new RuntimeException(
                    "The dataset has not been set. Please call setDataset(String).");

        return this.dataset;
    }

    /** simpler getter for {@link #files} */
    public ImportContainer[] getFilesAndDatasets()
    {
        return fads;
    }

    /** gets {@link Image} instance from {@link OMEROMetadataStore} */
    @SuppressWarnings("unchecked")
	public List<Image> getRoot()
    {
        return (List<Image>) store.getRoot();
    }

    // ~ Actions
    // =========================================================================


    /** opens the file using the {@link FormatReader} instance */
    public void open(String fileName) throws IOException, FormatException
    {
        /* test code ------
        Object[] args;
        
        args = new Object[1];
        args[0] = fileName;
        
        try {
            reader.setId(fileName);
            //reset series count
            log.debug("Image Count: " + reader.getImageCount());
        } catch (java.io.IOException e) {
            IOException(fileName);
        }*/
        
        reader.close();
        reader.setMetadataStore(store);
        reader.setMinMaxStore(store);
        reader.setId(fileName);
        //reset series count
        log.debug("Image Count: " + reader.getImageCount());
    }

    private void IOException(String fileName)
    {
        Object[] args;
        
        args = new Object[1];
        args[0] = fileName;
        notifyObservers(Actions.IO_EXCEPTION, args);
        //reset series count
        log.debug("IO Exception. Unable to retrieve image: " + fileName);
    }

    /**
     * calculates and returns the number of planes in this image. Also sets the
     * offset info.
     * 
     * @param fileName filename for use in {@link #setOffsetInfo(String)}
     * @return the number of planes in this image (z * c * t)
     */
    public int calculateImageCount(String fileName, Integer series)
    {
        List<Image> imageList = getRoot();
        // FIXME: This assumes only *one* Pixels. Image also missing iteratePixels
        Pixels pixels = (Pixels) ((ImageI) imageList.get(series)).iteratePixels().next();
        this.sizeZ = pixels.getSizeZ().getValue();
        this.sizeC = pixels.getSizeC().getValue();
        this.sizeT = pixels.getSizeT().getValue();
        this.sizeX = pixels.getSizeX().getValue();
        this.sizeY = pixels.getSizeY().getValue();
        int imageCount = sizeZ * sizeC * sizeT;
        setOffsetInfo(fileName);
        return imageCount;
    }

    /**
     * uses the {@link OMEROMetadataStore} to save the current {@link Pixels} to
     * the database.
     * 
     * @return the newly created {@link Pixels} id.
	 * @throws FormatException if there is an error parsing metadata.
	 * @throws IOException if there is an error reading the file.
     */
    @SuppressWarnings("unchecked")
	public List<Pixels> importMetadata(String imageName)
    	throws FormatException, IOException
    {
        List<Image> imageList = (List<Image>) store.getRoot();
        // Ensure that our metadata is consistent before writing to the DB.
        int series = 0;
        for (Image image : imageList)
        {
            int pixelCount = image.sizeOfPixels();
            
            // FIXME: This assumes only *one* set of pixels.
            //Pixels pix = (Pixels) image.iteratePixels().next();
            // FIXME: above problem is fixed
            for (int x = 0; x < pixelCount; x++)
            {
                Pixels pix = image.getPixels(x);

                String name = imageName;
                String seriesName = reader.getImageName(series);
                
                if (reader.getImageReader().isRGB() || reader.getImageReader().isIndexed())
                {
                    log.debug("Setting color channels to RGB format.");
                    if (pix.sizeOfChannels() == 3)
                    {
                        ColorI red = new ColorI();
                        red.setRed(rint(255));
                        red.setGreen(rint(0));
                        red.setBlue(rint(0));
                        red.setAlpha(rint(255));
                        
                        ColorI green = new ColorI();
                        green.setGreen(rint(255));
                        green.setRed(rint(0));
                        green.setBlue(rint(0));
                        green.setAlpha(rint(255)); 
                        
                        ColorI blue = new ColorI();
                        blue.setBlue(rint(255));
                        blue.setGreen(rint(0));
                        blue.setRed(rint(0));
                        blue.setAlpha(rint(255));            
                        
                        pix.getChannel(0).setColorComponent(red);
                        pix.getChannel(1).setColorComponent(green);
                        pix.getChannel(2).setColorComponent(blue);
                    }
                }

                if (seriesName != null && seriesName.length() != 0)
                    name += " [" + seriesName + "]";

                store.setImageName(name, series);
                if (pix.getPixelsDimensions() == null)
                {  
                    log.debug(String.format("Failsafe check: " +
                            "null PixelDimensions found, setting sizeX,Y,Z to 1.0f"));
                    
                    store.setDimensionsPhysicalSizeX(1.0f, series, x);
                    store.setDimensionsPhysicalSizeY(1.0f, series, x);
                    store.setDimensionsPhysicalSizeZ(1.0f, series, x);
                    //PixelsDimensionsI pixDims = new PixelsDimensionsI();
                    //pixDims.setSizeX(new RFloat(1.0f));
                    //pixDims.setSizeY(new RFloat(1.0f));
                    //pixDims.setSizeZ(new RFloat(1.0f));
                    //pix.setPixelsDimensions(pixDims);
                } else {   
                    log.debug(String.format("Failsafe check: " +
                            "PixelDimensions found with null sizes, setting sizeX,Y,Z to 1.0f"));
                    if (pix.getPixelsDimensions().getSizeX() == null)
                        store.setDimensionsPhysicalSizeX(1.0f, series, x);
                        //pix.getPixelsDimensions().setSizeX(new RFloat(1.0f));
                    if (pix.getPixelsDimensions().getSizeY() == null)
                        store.setDimensionsPhysicalSizeY(1.0f, series, x);
                        //pix.getPixelsDimensions().setSizeY(new RFloat(1.0f));
                    if (pix.getPixelsDimensions().getSizeZ() == null)
                        store.setDimensionsPhysicalSizeZ(1.0f, series, x);
                        //pix.getPixelsDimensions().setSizeZ(new RFloat(1.0f));
                }
            }
            series++;
        }
        
        log.debug("Saving pixels to DB.");
        
        List<Pixels> pixelsList = store.saveToDB();
        
        for (Pixels pixels : pixelsList)
        {
            Image image = pixels.getImage();
            store.addImageToDataset(image, dataset);
        }
        
        return pixelsList;
    }

    /**
     * Retrieves how many bytes per pixel the current plane or section has.
     * @return the number of bytes per pixel.
     */
    private int getBytesPerPixel(int type) {
      switch(type) {
      case 0:
      case 1:
        return 1;  // INT8 or UINT8
      case 2:
      case 3:
        return 2;  // INT16 or UINT16
      case 4:
      case 5:
      case 6:
        return 4;  // INT32, UINT32 or FLOAT
      case 7:
        return 8;  // DOUBLE
      }
      throw new RuntimeException("Unknown type with id: '" + type + "'");
    }

    /**
     * @param file
     * @param index
     * @param total Import the actual image planes
     * @param b 
     * @throws FormatException if there is an error parsing metadata.
     * @throws IOException if there is an error reading the file.
     */
    // TODO: Add observer messaging for any agnostic viewer class to use
    @SuppressWarnings("unused")
    public List<Pixels> importImage(File file, int index, int numDone, int total, String imageName, boolean archive)
    throws FormatException, IOException, ServerError
    {        
        String fileName = file.getAbsolutePath();
        String shortName = file.getName();
        Object[] args;
        
        args = new Object[9];
        args[0] = shortName;
        args[1] = index;
        args[2] = numDone;
        args[3] = total;

        notifyObservers(Actions.LOADING_IMAGE, args);

        open(file.getAbsolutePath());
        
        notifyObservers(Actions.LOADED_IMAGE, args);
        
        String[] fileNameList = reader.getUsedFiles();
        File[] files = new File[fileNameList.length];
        for (int i = 0; i < fileNameList.length; i++) 
        {
            files[i] = new File(fileNameList[i]); 
        }
        String formatString = reader.getImageReader().getReader().getClass().toString();
        formatString = formatString.replace("class loci.formats.in.", "");
        formatString = formatString.replace("Reader", "");
        System.err.println(formatString);
        if (archive == true)
        {
            store.setOriginalFiles(files, formatString);
        }
        
        reader.getUsedFiles();
        
        List<Pixels> pixList = importMetadata(imageName);

        int seriesCount = reader.getSeriesCount();
        
        for (int series = 0; series < seriesCount; series++)
        {
            int count = calculateImageCount(fileName, series);
            long pixId = pixList.get(series).getId().getValue(); 
            
            args[4] = getDataset();
            args[5] = pixId;
            args[6] = count;
            args[7] = series;
            
            notifyObservers(Actions.DATASET_STORED, args);
            
            BooleanAnnotationI annotation = new BooleanAnnotationI();
            annotation.setBoolValue(rbool(archive));
            annotation.setNs(rstring("openmicroscopy.org/omero/importer/archived")); // openmicroscopy.org/omero/importer/archived
            
            store.addBooleanAnnotationToPixels(annotation, pixList.get(series));
            
            importData(pixId, fileName, series, new ImportLibrary.Step()
            {
                @Override
                public void step(int series, int step)
                {
                    Object args2[] = {series, step, reader.getSeriesCount()};
                    notifyObservers(Actions.IMPORT_STEP, args2);
                }
            });
            
            notifyObservers(Actions.DATA_STORED, args);  
           
            if (archive == true)
            {
                //qTable.setProgressArchiving(index);
                for (int i = 0; i < fileNameList.length; i++) 
                {
                    files[i] = new File(fileNameList[i]);
                    store.writeFilesToFileStore(files, pixId);   
                }
            }
        }
        
        notifyObservers(Actions.IMPORT_DONE, args);
        
        return pixList;
        
    }
    
    /**
     * saves the binary data to the server. After each successful save,
     * {@link Step#step(int)} is called with the number of the iteration just
     * completed.
     * @param series 
     */
    public void importData(Long pixId, String fileName, int series, Step step)
    throws FormatException, IOException, ServerError
    {
        int i = 1;
        int bytesPerPixel = getBytesPerPixel(reader.getPixelType());
        byte[] arrayBuf = new byte[sizeX * sizeY * bytesPerPixel];

        reader.setSeries(series);
        MessageDigest md;
        
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    "Required SHA-1 message digest algorithm unavailable.");
        }
        
        store.setPixelsId(pixId);
        
        FileChannel wChannel = null;
        File f;
        
        if (dumpPixels)
        {
            f = new File("pixeldump");
            boolean append = true;
            wChannel = new FileOutputStream(f, append).getChannel();   
        }
        
        for (int t = 0; t < sizeT; t++)
        {
            for (int c = 0; c < sizeC; c++)
            {
                for (int z = 0; z < sizeZ; z++)
                {
                    int planeNumber = reader.getIndex(z, c, t);
                    //int planeNumber = getTotalOffset(z, c, t);
                    ByteBuffer buf =
                        reader.openPlane2D(fileName, planeNumber, arrayBuf).getData();
                    arrayBuf = swapIfRequired(buf, fileName);
                    try {
                        md.update(arrayBuf);
                    } catch (Exception e) {
                        // This better not happen. :)
                        throw new RuntimeException(e);
                    }
                    step.step(series, i);
                    store.setPlane(pixId, arrayBuf, z, c, t);
                    if (dumpPixels)
                        wChannel.write(buf);
                    i++;
                }
            }
        }
        
        if (dumpPixels)
            wChannel.close();
        reader.populateSHA1(pixId, md);
        reader.populateMinMax(pixId, series);
    }
    
    // ~ Helpers
    // =========================================================================

    private void setOffsetInfo(String fileName)
    {
        int order = 0;
        order = getSequenceNumber(reader.getDimensionOrder());
        setOffsetInfo(order, sizeZ, sizeC, sizeT);
    }

    /**
     * This method calculates the size of a w, t, z section depending on which
     * sequence is being used (either ZTW, WZT, or ZWT)
     * 
     * @param imgSequence
     * @param numZSections
     * @param numWaves
     * @param numTimes
     */
    private void setOffsetInfo(int imgSequence, int numZSections, int numWaves,
            int numTimes)
    {
        int smallOffset = 1;
        switch (imgSequence)
        {
            // ZTW sequence
            case 0:
                zSize = smallOffset;
                tSize = zSize * numZSections;
                wSize = tSize * numTimes;
                break;
            // WZT sequence
            case 1:
                wSize = smallOffset;
                zSize = wSize * numWaves;
                tSize = zSize * numZSections;
                break;
            // ZWT sequence
            case 2:
                zSize = smallOffset;
                wSize = zSize * numZSections;
                tSize = wSize * numWaves;
                break;
            // TWZ sequence
            case 3:
                tSize = smallOffset;
                wSize = tSize * numTimes;
                zSize = wSize * numWaves;
                break;
            // WTZ sequence
            case 4:
                wSize = smallOffset;
                tSize = wSize * numWaves;
                zSize = tSize * numTimes;
                break;
            //TZW
            case 5:
                tSize = smallOffset;
                zSize = wSize * numTimes;
                wSize = tSize * numZSections;
                
        }
    }

    /**
     * Given any specific Z, W, and T, determine the totalOffset from the start
     * of the file
     * 
     * @param currentZ
     * @param currentW
     * @param currentT
     * @return
     */
    private int getTotalOffset(int currentZ, int currentW, int currentT)
    {
        return (zSize * currentZ) + (wSize * currentW) + (tSize * currentT);
    }

    private int getSequenceNumber(String dimOrder)
    {
        if (dimOrder.equals("XYZTC")) return 0;
        if (dimOrder.equals("XYCZT")) return 1;
        if (dimOrder.equals("XYZCT")) return 2;
        if (dimOrder.equals("XYTCZ")) return 3;
        if (dimOrder.equals("XYCTZ")) return 4;
        if (dimOrder.equals("XYTZC")) return 5;
        throw new RuntimeException(dimOrder + " not represented in " +
                "getSequenceNumber");
    }
    
    /** Return true if the data is in little-endian format. 
     * @throws IOException 
     * @throws FormatException */
    public boolean isLittleEndian(String fileName) throws FormatException, IOException {
      return reader.isLittleEndian();
    }
    
    /**
     * Examines a byte array to see if it needs to be byte swapped and modifies
     * the byte array directly.
     * @param byteArray The byte array to check and modify if required.
     * @return the <i>byteArray</i> either swapped or not for convenience.
     * @throws IOException if there is an error read from the file.
     * @throws FormatException if there is an error during metadata parsing.
     */
    private byte[] swapIfRequired(ByteBuffer buffer, String fileName)
    throws FormatException, IOException
  {
    int pixelType = reader.getPixelType();
    int bytesPerPixel = getBytesPerPixel(pixelType);
    
    // We've got nothing to do if the samples are only 8-bits wide.
    if (bytesPerPixel == 1) 
        return buffer.array();

    int length;
    
    //System.err.println(fileName + " is Little Endian: " + isLittleEndian(fileName));
    if (isLittleEndian(fileName)) {
      if (bytesPerPixel == 2) { // short
        ShortBuffer buf = buffer.asShortBuffer();
        length = buffer.capacity() / 2;
        short x;
        for (int i = 0; i < length; i++) {
          x = buf.get(i);
          buf.put(i, (short) ((x << 8) | ((x >> 8) & 0xFF)));
        }
      } else if (bytesPerPixel == 4) { // int/uint/float
          IntBuffer buf = buffer.asIntBuffer();
          length = buffer.capacity() / 4;
          for (int i = 0; i < length; i++) {
            buf.put(i, DataTools.swap(buf.get(i)));
          }
      } else if (bytesPerPixel == 8) // double
      {
          LongBuffer buf = buffer.asLongBuffer();
          length = buffer.capacity() / 8;
          for (int i = 0; i < length ; i++) {
            buf.put(i, DataTools.swap(buf.get(i)));
          }
      } else {
        throw new FormatException(
          "Unsupported sample bit width: '" + bytesPerPixel + "'");
      }
    }
    // We've got a big-endian file with a big-endian byte array.
    return buffer.array();
  }
    
    // Observable methods
    
    public boolean addObserver(IObserver object)
    {
        return observers.add(object);
    }
    
    public boolean deleteObserver(IObserver object)
    {
        return observers.remove(object);
        
    }

    public void notifyObservers(Object message, Object[] args)
    {
        for (IObserver observer:observers)
        {
            observer.update(this, message, args);
        }
    }
}
