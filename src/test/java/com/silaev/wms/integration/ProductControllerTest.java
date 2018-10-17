package com.silaev.wms.integration;

import com.silaev.wms.annotation.version.ApiV1;
import com.silaev.wms.converter.ProductToProductDtoConverter;
import com.silaev.wms.dao.ProductDao;
import com.silaev.wms.dto.ProductDto;
import com.silaev.wms.entity.Brand;
import com.silaev.wms.entity.Product;
import com.silaev.wms.entity.Size;
import com.silaev.wms.security.SecurityConfig;
import com.silaev.wms.util.ProductUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class ProductControllerTest {

    public static final String BASE_URL = ApiV1.BASE_URL;

    @Autowired
    private WebTestClient webClient;

    @Autowired
    private ProductToProductDtoConverter productConverter;

    @Autowired
    private ProductDao productDao;

    private Product product1;
    private Product product2;
    private Product product3;
    private ProductDto productDto1;
    private ProductDto productDto2;
    private ProductDto productDto3;

    @Before
    public void setUp() {
        product1 = ProductUtil.mockProduct(120589L, "AAA", Brand.DOLCE, BigDecimal.valueOf(9), 9, Size.SIZE_50);
        product2 = ProductUtil.mockProduct(120590L, "BBB", Brand.DOLCE, BigDecimal.valueOf(15.69), 6, Size.SIZE_100);
        product3 = ProductUtil.mockProduct(120591L, "CCC", Brand.ENGLISH_LAUNDRY, BigDecimal.valueOf(55.12), 3, Size.SIZE_100);

        productDto1 = productConverter.convert(product1);
        productDto2 = productConverter.convert(product2);
        productDto3 = productConverter.convert(product3);
    }

    @After
    public void tearDown() {
        StepVerifier.create(productDao.deleteAll()).verifyComplete();
    }

    @WithMockUser(authorities = SecurityConfig.READ_PRIVILEGE)
    @Test
    public void shouldFindProductsByNameOrBrand() {
        //GIVEN
        insertMockProductsIntoDb(Arrays.asList(product1, product2, product3));

        //WHEN
        WebTestClient.ResponseSpec exchange = webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path(BASE_URL + "/all")
                        .queryParam("name", "AAA")
                        .queryParam("brand", ProductUtil.encodeQueryParam(Brand.ENGLISH_LAUNDRY.getBrandName()))
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange();
        //THEN
        exchange
                .expectStatus()
                .isOk()
                .expectBodyList(ProductDto.class)
                .contains(productDto1, productDto3)//without order
                .hasSize(2);
    }

    @WithMockUser(authorities = SecurityConfig.READ_PRIVILEGE)
    @Test
    public void shouldFindAll() {
        //GIVEN
        insertMockProductsIntoDb(Arrays.asList(product1, product2, product3));

        //WHEN
        WebTestClient.ResponseSpec exchange = webClient
                .get()
                .uri(BASE_URL + "/admin/all")
                .accept(MediaType.APPLICATION_JSON)
                .exchange();
        //THEN
        exchange
                .expectStatus()
                .isOk()
                .expectBodyList(Product.class)
                .contains(product1, product2, product3)
                .hasSize(3);
    }

    @WithMockUser(authorities = SecurityConfig.READ_PRIVILEGE)
    @Test
    public void shouldFindLastProducts() {
        //GIVEN
        //log.debug("After insert product1:{} product2:{} product3:{}",
        // product1.getQuantity(), product2.getQuantity(), product3.getQuantity());
        insertMockProductsIntoDb(Arrays.asList(product1, product2, product3));

        //WHEN
        WebTestClient.ResponseSpec exchange = webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path(BASE_URL + "/last")
                        //.queryParam("lastSize", "5") by default
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange();
        //THEN
        exchange
                .expectStatus()
                .isOk()
                .expectBodyList(ProductDto.class)
                .contains(productDto3)
                .hasSize(1);
    }

    /**
     * products.xlsx's content:
     * article	name	        quantity	size    initialQuantity     expextedQuantity
     * ---------------------------------------------------------------------------------
     * 120589	Eau de Parfum	7	        50      9                   7+9=16
     * 120590	Eau de Parfum	21	        100     6                   21+6=27
     * 1647	    Eau de Parfum	79	        50                          NON cause article 1647 doesn't exist
     */

    @WithMockUser(
            username = SecurityConfig.ADMIN_NAME,
            password = SecurityConfig.ADMIN_PAS,
            authorities = SecurityConfig.WRITE_PRIVILEGE
    )
    @Test
    public void shouldPatchProductQuantity() {
        //GIVEN
        insertMockProductsIntoDb(Arrays.asList(product1, product2));

        //WHEN
        WebTestClient.ResponseSpec exchange = webClient
                .patch()
                .uri(BASE_URL)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(ProductUtil.getMultiPartFormData()))
                .exchange();

        //THEN
        exchange
                .expectStatus()
                .isOk();

        Flux<Product> all = productDao.findAll().sort(Comparator.comparing(Product::getQuantity));
        StepVerifier.create(all)
                .assertNext(x -> assertEquals(BigInteger.valueOf(16), x.getQuantity()))
                .assertNext(x -> assertEquals(BigInteger.valueOf(27), x.getQuantity()))
                .verifyComplete();
    }

    @WithMockUser(authorities = SecurityConfig.WRITE_PRIVILEGE)
    @Test
    public void shouldCreateProducts() {
        //GIVEN
        Flux<ProductDto> dtoFlux = Flux.just(productDto1, productDto2);

        //WHEN
        WebTestClient.ResponseSpec exchange = webClient.post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(dtoFlux, ProductDto.class)
                .exchange();

        //THEN
        exchange.expectStatus()
                .isCreated()
                .expectBodyList(Product.class)
                .contains(product1, product2)
                .hasSize(2);
    }

    /**
     * Hepler method to insert mock products into MongoDB
     *
     * @param products
     */
    private void insertMockProductsIntoDb(List<Product> products) {
        Flux<Product> productFlux = Flux.fromIterable(products);
        Flux<Product> insert = productDao.insert(productFlux);
        StepVerifier.create(insert)
                .expectNextSequence(products)
                .verifyComplete();
    }
}