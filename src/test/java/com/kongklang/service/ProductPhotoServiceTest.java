package com.kongklang.service;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import com.kongklang.repository.ProductRepository;
import com.kongklang.entity.Product;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProductPhotoServiceTest {
 @Test void resizesAndReencodesActualImage() throws Exception {
  var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(1600,800,BufferedImage.TYPE_INT_RGB),"PNG",output);
  byte[] data=ProductPhotoService.normalize(new MockMultipartFile("photo","photo.png","image/png",output.toByteArray()));
  var image=ImageIO.read(new ByteArrayInputStream(data));assertEquals(960,image.getWidth());assertEquals(480,image.getHeight());assertEquals(0xff,data[0]&255);assertEquals(0xd8,data[1]&255);
 }
 @Test void rejectsDisguisedScriptAndOversize() {
  assertThrows(IllegalArgumentException.class,()->ProductPhotoService.normalize(new MockMultipartFile("photo","x.png","image/png","<svg onload='alert(1)'/>".getBytes())));
  assertThrows(IllegalArgumentException.class,()->ProductPhotoService.normalize(new MockMultipartFile("photo","x.jpg","image/jpeg",new byte[5*1024*1024+1])));
 }
 @Test void crossStoreReadNeverQueriesPhotoData() {
  var access=mock(AccessService.class);var repo=mock(ProductRepository.class);var jdbc=mock(JdbcTemplate.class);var session=new MockHttpSession();
  when(access.require(session)).thenReturn(new AccessService.Actor(2L,20L,"OWNER"));when(repo.findByIdAndStoreId(1L,20L)).thenReturn(Optional.empty());
  var service=new ProductPhotoService(access,repo,mock(ProductService.class),jdbc);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.read(1L,session));verifyNoInteractions(jdbc);
 }
 @Test void crossStoreWriteCannotChangeProductOrPhoto() {
  var access=mock(AccessService.class);var repo=mock(ProductRepository.class);var jdbc=mock(JdbcTemplate.class);var products=mock(ProductService.class);var session=new MockHttpSession();
  when(access.require(session,"OWNER","ADMIN")).thenReturn(new AccessService.Actor(2L,20L,"OWNER"));when(repo.findByIdAndStoreId(1L,20L)).thenReturn(Optional.empty());
  var service=new ProductPhotoService(access,repo,products,jdbc);var p=new Product();p.setId(1L);
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.save(p,null,true,session));verifyNoInteractions(jdbc,products);
 }
}
