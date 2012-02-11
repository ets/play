package play.server;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import play.Logger;
public class ChunkedInputStream extends InputStream {

    /**
     * Queue of inputStreams that have yet to be read from. 
     */
    private BlockingQueue<InputStream> inputStreamQueue = new LinkedBlockingQueue<InputStream>();
    /**
     * A cache of the current inputStream from the inputStreamQueue to avoid unneeded access to the queue 
     */
    private volatile InputStream currentInputStream = null;
    /**
     * true iff all InputStreams have been added
     */
    private volatile boolean lastInputStreamAppended = false;
    /**
     * True iff this the close() method has been called on this stream. 
     */
    private volatile boolean closed = false;

    /**
     * Create a new input stream that can dynamically accept new sources.
     * <p>
     * New sources should be added using the addInputStream() method. When all sources have been added the lastInputStreamAdded() should be called so that read
     * methods can return -1 (end of stream).
     * <p>
     * Adding new sources may by interleaved with read calls.
     * 
     */
    public ChunkedInputStream() {
    }
    
    /**
     * Causes the addInputStream method to throw IllegalStateException and read() methods to return -1 (end of stream) when there is no more available data.
     * <p>
     * Calling this method when this class is no longer accepting more inputStreams has no effect.
     */
    public void lastInputStreamAdded() {
        lastInputStreamAppended = true;
    }
    /**
     * Add the given inputStream to the queue of inputStreams from which to concatenate data.
     * 
     * @param in
     *            InputStream to add to the concatenation.
     * @throws IllegalStateException
     *             if more inputStreams can't be added because lastInputStreamAdded() has been called, close() has been called, or a constructor with
     *             inputStream parameters was used.
     * 
     */
    public void addInputStream(InputStream in) {
        if(Logger.isDebugEnabled()) Logger.debug("Adding InputStream to ChunkedInputStream");        
        if (in == null)
            throw new NullPointerException();
        if (closed)
            throw new IllegalStateException("ChunkedInputStream has been closed");
        if (lastInputStreamAppended)
            throw new IllegalStateException("Cannot add more inputStreams - the last inputStream has already been added.");
        inputStreamQueue.add(in);
        
    }
    public int getInputStreamQueueSize(){
        return inputStreamQueue.size();
    }
    public boolean isClosed(){
        return closed;
    }
    private final InputStream getCurrentInputStream() {
        if (currentInputStream == null && inputStreamQueue.size() > 0) {            
            currentInputStream = inputStreamQueue.poll();            
        }
        return currentInputStream;
    }    
    private final void advanceToNextInputStream() {
        currentInputStream = null;
    }    
    /**
     * Reads the next byte of data from the underlying streams. The value byte is returned as an int in the range 0 to 255. If no byte is available because the
     * end of the stream has been reached, the value -1 is returned. This method blocks until input data is available, the end of the stream is detected, or an
     * exception is thrown.
     * <p>
     * If this class in not done accepting input streams and the end of the last known stream is reached, this method will block forever unless another thread
     * adds an input stream or interrupts.
     * 
     * @return the next byte of data, or -1 if the end of the stream is reached.
     * 
     * @throws IOException
     *             if an I/O error occurs.
     */
    @Override
    public int read() throws IOException {
        if (closed)
            throw new IOException("InputStream closed");
        int r = -1;
        while (r == -1) {
            InputStream in = getCurrentInputStream();
            if (in == null) {
                if (lastInputStreamAppended)
                    return -1;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException iox) {
                    throw new IOException("Interrupted");
                }
            } else {
                r = in.read();
                if (r == -1)
                    advanceToNextInputStream();
            }
        }
        return r;
    }
    /**
     * Reads some number of bytes from the underlying streams and stores them into the buffer array b. The number of bytes actually read is returned as an
     * integer. This method blocks until input data is available, end of file is detected, or an exception is thrown.
     * <p>
     * If the length of b is zero, then no bytes are read and 0 is returned; otherwise, there is an attempt to read at least one byte.
     * <p>
     * The read(b) method for class InputStream has the same effect as:<br>
     * read(b, 0, b.length)
     * <p>
     * If this class in not done accepting input streams and the end of the last known stream is reached, this method will block forever unless another thread
     * adds an input stream or interrupts.
     * 
     * @param b
     *            - Destination buffer
     * @return The number of bytes read, or -1 if the end of the stream has been reached
     * 
     * @throws IOException
     *             - If an I/O error occurs
     * @throws NullPointerException
     *             - If b is null.
     * 
     */
    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }
    /**
     * Reads up to length bytes of data from the underlying streams into an array of bytes. An attempt is made to read as many as length bytes, but a smaller
     * number may be read, possibly zero. The number of bytes actually read is returned as an integer.
     * <p>
     * If length is zero, then no bytes are read and 0 is returned; otherwise, there is an attempt to read at least one byte.
     * <p>
     * This method blocks until input data is available
     * <p>
     * If this class in not done accepting input streams and the end of the last known stream is reached, this method will block forever unless another thread
     * adds an input stream or interrupts.
     * 
     * @param b
     *            Destination buffer
     * @param off
     *            Offset at which to start storing bytes
     * @param len
     *            Maximum number of bytes to read
     * @return The number of bytes read, or -1 if the end of the stream has been reached
     * 
     * @throws IOException
     *             - If an I/O error occurs
     * @throws NullPointerException
     *             - If b is null.
     * @throws IndexOutOfBoundsException
     *             - if length or offset are not possible.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IllegalArgumentException();
        if (closed)
            throw new IOException("InputStream closed");
        int r = -1;
        while (r == -1) {
            InputStream in = getCurrentInputStream();
            if (in == null) {
                if (lastInputStreamAppended)
                    return -1;
                try {
                    Thread.sleep(10);                    
                } catch (InterruptedException iox) {
                    throw new IOException("Interrupted");
                }
            } else {
                r = in.read(b, off, len);
                if (r == -1)
                    advanceToNextInputStream();
            }
        }
        return r;
    }
    /**
     * Skips over and discards n bytes of data from this input stream. The skip method may, for a variety of reasons, end up skipping over some smaller number
     * of bytes, possibly 0. This may result from any of a number of conditions; reaching end of file before n bytes have been skipped is only one possibility.
     * The actual number of bytes skipped is returned. If n is negative, no bytes are skipped.
     * <p>
     * If this class in not done accepting input streams and the end of the last known stream is reached, this method will block forever unless another thread
     * adds an input stream or interrupts.
     * 
     * @param n
     *            he number of characters to skip
     * @return The number of characters actually skipped
     * 
     * @throws IOException
     *             If an I/O error occurs
     * 
     */
    @Override
    public long skip(long n) throws IOException {
        if (closed)
            throw new IOException("InputStream closed");
        if (n <= 0)
            return 0;
        long s = -1;
        while (s <= 0) {
            InputStream in = getCurrentInputStream();
            if (in == null) {
                if (lastInputStreamAppended)
                    return 0;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException iox) {
                    throw new IOException("Interrupted");
                }
            } else {
                s = in.skip(n);
                // When nothing was skipped it is a bit of a puzzle.
                // The most common cause is that the end of the underlying
                // stream was reached. In which case calling skip on it
                // will always return zero. If somebody were calling skip
                // until it skipped everything they needed, there would
                // be an infinite loop if we were to return zero here.
                // If we get zero, let us try to read one character so
                // we can see if we are at the end of the stream. If so,
                // we will move to the next.
                if (s <= 0) {
                    // read() will advance to the next stream for us, so don't do it again
                    s = ((read() == -1) ? -1 : 1);
                }
            }
        }
        return s;
    }
    /**
     * Returns the number of bytes that can be read (or skipped over) from this input stream without blocking by the next caller of a method for this input
     * stream. The next caller might be the same thread or or another thread.
     * 
     * @throws IOException
     *             If an I/O error occurs
     * 
     */
    @Override
    public int available() throws IOException {
        if (closed)
            throw new IOException("InputStream closed");
        InputStream in = getCurrentInputStream();
        if (in == null)
            return 0;
        return in.available();
    }
    /**
     * Closes this input stream and releases any system resources associated with the stream.
     * 
     */
    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        if(Logger.isDebugEnabled()) Logger.debug("Closing ChunkedInputStream...");
        for (Object element : inputStreamQueue) {
            ((InputStream) element).close();
        }        
        
    }
    /**
     * Mark not supported
     * 
     */
    @Override
    public void mark(int readlimit) {
        throw new RuntimeException();
    }
    /**
     * Reset not supported.
     * 
     * @throws IOException
     *             because reset is not supported.
     * 
     */
    @Override
    public void reset() throws IOException {
        throw new IOException("Reset not supported");
    }
    /**
     * Does not support mark.
     * 
     * @return false
     * 
     */
    @Override
    public boolean markSupported() {
        return false;
    }
}