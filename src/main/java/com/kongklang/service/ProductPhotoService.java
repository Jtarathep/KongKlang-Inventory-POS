package com.kongklang.service;

import com.kongklang.entity.Product;
import com.kongklang.repository.ProductRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

/** Store-scoped photos are never exposed through a public static upload directory. */
@Service
public class ProductPhotoService {
    private final AccessService access;
    private final ProductRepository products;
    private final ProductService service;
    private final JdbcTemplate jdbc;
    public ProductPhotoService(AccessService access, ProductRepository products, ProductService service, JdbcTemplate jdbc) {
        this.access=access; this.products=products; this.service=service; this.jdbc=jdbc;
    }
    @Transactional
    public void save(Product product, MultipartFile file, boolean remove, HttpSession session) {
        var actor=access.require(session,"OWNER","ADMIN");
        if(product.getId()!=null) owned(product.getId(),actor.storeId());
        byte[] image=file==null||file.isEmpty()?null:normalize(file);
        if(product.getId()==null) service.saveProduct(product,session);
        else service.updateProduct(product.getId(),product,session);
        if(image!=null) jdbc.update("INSERT INTO product_photos (product_id,store_id,image_data) VALUES (?,?,?) ON DUPLICATE KEY UPDATE image_data=VALUES(image_data)",product.getId(),actor.storeId(),image);
        else if(remove) jdbc.update("DELETE FROM product_photos WHERE product_id=? AND store_id=?",product.getId(),actor.storeId());
    }
    private void owned(Long id,Long store) {
        if(products.findByIdAndStoreId(id,store).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @Transactional(readOnly=true)
    public byte[] read(Long id,HttpSession session) {
        var actor=access.require(session); owned(id,actor.storeId());
        return jdbc.query("SELECT image_data FROM product_photos WHERE product_id=? AND store_id=?",(rs,n)->rs.getBytes(1),id,actor.storeId()).stream().findFirst().orElse(null);
    }
    static byte[] normalize(MultipartFile file) {
        if(file.getSize()>5*1024*1024) throw new IllegalArgumentException("รูปต้องมีขนาดไม่เกิน 5 MB");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new IllegalArgumentException("กรุณาเลือกรูป JPG หรือ PNG ที่เปิดได้");
            ImageReader reader=readers.next();
            try {
                reader.setInput(input,true,true);
                String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("JPEG")&&!format.equalsIgnoreCase("PNG")) throw new IllegalArgumentException("รองรับเฉพาะ JPG และ PNG");
                int w=reader.getWidth(0), h=reader.getHeight(0);
                if(w<1||h<1||w>8192||h>8192||(long)w*h>24000000) throw new IllegalArgumentException("รูปมีความละเอียดสูงเกินไป กรุณาย่อรูปก่อน");
                BufferedImage source=reader.read(0);double scale=Math.min(1,960.0/Math.max(w,h));
                BufferedImage target=new BufferedImage(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=target.createGraphics();try {g.setColor(Color.WHITE);g.fillRect(0,0,target.getWidth(),target.getHeight());g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(source,0,0,target.getWidth(),target.getHeight(),null);} finally {g.dispose();}
                var output=new ByteArrayOutputStream();ImageIO.write(target,"JPEG",output);return output.toByteArray();
            } finally {reader.dispose();}
        } catch(IOException e) {throw new IllegalArgumentException("อ่านรูปไม่สำเร็จ กรุณาเลือกรูปใหม่");}
    }
}
