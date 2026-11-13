package tw.ipola;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Decodes bounded raster images and emits a fresh JPEG without input metadata. */
final class PrivateImage {
 private PrivateImage() {}
 static byte[] decode(MultipartFile file) {
  if(file.isEmpty())throw bad();
  if(file.getSize()>5*1024*1024)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
  try {
   byte[] bytes=file.getBytes();
   if(bytes.length>5*1024*1024)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
   boolean jpeg=bytes.length>3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255;
   boolean png=bytes.length>8 && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
   if(!jpeg&&!png)throw bad();
   try(ImageInputStream input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
    var readers=ImageIO.getImageReaders(input);
    if(!readers.hasNext())throw bad();
    ImageReader reader=readers.next();
    try {
     reader.setInput(input,false,true);
     String format=reader.getFormatName();
     if(!format.equalsIgnoreCase("JPEG")&&!format.equalsIgnoreCase("PNG"))throw bad();
     int width=reader.getWidth(0),height=reader.getHeight(0);
     if(width<=0||height<=0||(long)width*height>12_000_000)throw bad();
     if(png && animatedPng(bytes))throw bad();
     BufferedImage source=reader.read(0);
     int orientation=jpeg?orientation(bytes):1;
     BufferedImage oriented=orient(source,orientation);
     double scale=Math.min(1,1600.0/Math.max(oriented.getWidth(),oriented.getHeight()));
     int outWidth=Math.max(1,(int)Math.round(oriented.getWidth()*scale));
     int outHeight=Math.max(1,(int)Math.round(oriented.getHeight()*scale));
     BufferedImage result=new BufferedImage(outWidth,outHeight,BufferedImage.TYPE_INT_RGB);
     Graphics2D g=result.createGraphics();
     try {g.setColor(Color.WHITE);g.fillRect(0,0,outWidth,outHeight);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(oriented,0,0,outWidth,outHeight,null);}
     finally {g.dispose();}
     var output=new ByteArrayOutputStream();
     try(var sink=new MemoryCacheImageOutputStream(output)) {
      if(!ImageIO.write(result,"JPEG",sink))throw bad();
     }
     return output.toByteArray();
    } finally {reader.dispose();}
   }
  }catch(IOException|IllegalArgumentException e){throw bad();}
 }
 private static boolean animatedPng(byte[] bytes) {
  int i=8;
  while(i+12<=bytes.length) {
   long length=((long)(bytes[i]&255)<<24)|((long)(bytes[i+1]&255)<<16)|((long)(bytes[i+2]&255)<<8)|(bytes[i+3]&255);
   if(length>bytes.length-i-12)throw bad();
   if(bytes[i+4]=='a'&&bytes[i+5]=='c'&&bytes[i+6]=='T'&&bytes[i+7]=='L')return true;
   i+=(int)length+12;
  }
  return false;
 }
 private static BufferedImage orient(BufferedImage source,int orientation) {
  int w=source.getWidth(),h=source.getHeight();boolean swap=orientation>=5&&orientation<=8;
  BufferedImage target=new BufferedImage(swap?h:w,swap?w:h,BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
   int dx=x,dy=y;
   switch(orientation) {
    case 2 -> dx=w-1-x;
    case 3 -> {dx=w-1-x;dy=h-1-y;}
    case 4 -> dy=h-1-y;
    case 5 -> {dx=y;dy=x;}
    case 6 -> {dx=h-1-y;dy=x;}
    case 7 -> {dx=h-1-y;dy=w-1-x;}
    case 8 -> {dx=y;dy=w-1-x;}
    default -> {}
   }
   target.setRGB(dx,dy,source.getRGB(x,y));
  }
  return target;
 }
 // Read only the EXIF orientation SHORT in IFD0; all metadata is discarded on output.
 private static int orientation(byte[] b) {
  int p=2;
  while(p+4<b.length) {
   if((b[p]&255)!=255)break;
   int marker=b[p+1]&255;p+=2;
   if(marker==218||marker==217)break;
   int length=((b[p]&255)<<8)|(b[p+1]&255);
   if(length<2||p+length>b.length)break;
   if(marker==225&&length>=16&&b[p+2]=='E'&&b[p+3]=='x'&&b[p+4]=='i'&&b[p+5]=='f'&&b[p+6]==0&&b[p+7]==0) {
    int t=p+8,end=p+length;boolean little=b[t]=='I'&&b[t+1]=='I';
    if(!little&&!(b[t]=='M'&&b[t+1]=='M'))return 1;
    if(read(b,t+2,2,little)!=42)return 1;
    long offset=read(b,t+4,4,little);
    if(offset<8||offset>end-t-2)return 1;
    int ifd=t+(int)offset;int count=(int)read(b,ifd,2,little);
    for(int n=0;n<count;n++) {
     int entry=ifd+2+n*12;if(entry+12>end)return 1;
     if(read(b,entry,2,little)==274&&read(b,entry+2,2,little)==3&&read(b,entry+4,4,little)==1) {
      int value=(int)read(b,entry+8,2,little);return value>=1&&value<=8?value:1;
     }
    }
   }
   p+=length;
  }
  return 1;
 }
 private static long read(byte[] b,int p,int size,boolean little) {
  long result=0;for(int n=0;n<size;n++)result|=(long)(b[p+n]&255)<<(8*(little?n:size-1-n));return result;
 }
 private static ResponseStatusException bad(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid image");}
}
