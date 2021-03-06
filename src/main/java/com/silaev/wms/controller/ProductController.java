package com.silaev.wms.controller;

import com.silaev.wms.annotation.version.ApiV1;
import com.silaev.wms.converter.StringToBrandConverter;
import com.silaev.wms.dto.FileUploadDto;
import com.silaev.wms.dto.ProductDto;
import com.silaev.wms.model.Brand;
import com.silaev.wms.service.ProductService;
import com.silaev.wms.service.UploadProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import javax.validation.constraints.Min;
import java.math.BigInteger;
import java.security.Principal;

@RestController
@ApiV1
@RequiredArgsConstructor
@Slf4j
@Validated
public class ProductController {
  private final ProductService productService;
  private final UploadProductService uploadProductService;

  @InitBinder
  public void initBinder(WebDataBinder dataBinder) {
    dataBinder.registerCustomEditor(Brand.class, new StringToBrandConverter());
  }

  /**
   * Gets products in the ProductDto representation.
   *
   * @param name  RequestParam
   * @param brand RequestParam
   * @return Flux<ProductDto>
   */
  @GetMapping(value = "/all", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
  public ResponseEntity<Flux<ProductDto>> findProductsByNameOrBrand(
    @RequestParam(value = "name", required = false) String name,
    @RequestParam(value = "brand", required = false) Brand brand
  ) {
    log.debug("findProductsByNameOrBrand: {}, {}", name, brand);

    if ((name == null) && (brand == null)) {
      throw new IllegalArgumentException("Neither name nor brand had been set as request param.");
    }

    return ResponseEntity.ok(productService.findProductsByNameOrBrand(name, brand));
  }

  /**
   * Gets products in the Product entity representation
   * for administrative needs.
   *
   * @return Flux<ProductDto>
   */
  @GetMapping(value = "/admin/all", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
  public ResponseEntity<Flux<ProductDto>> findAll() {
    log.debug("findAll");

    return ResponseEntity.ok(productService.findAll());
  }

  /**
   * Gets products whose quantity are less or equal a listSize.
   *
   * @param lastSize - a number representing listSize. Default value is 5.
   * @return Flux<ProductDto>
   */
  @GetMapping(value = "/last", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
  public ResponseEntity<Flux<ProductDto>> findLastProducts(
    @RequestParam(value = "lastSize", required = false, defaultValue = "5")
    @Min(value = 1) BigInteger lastSize) {
    log.debug("findLastProducts: {}", lastSize);

    return ResponseEntity.ok(productService.findLastProducts(lastSize));
  }

  /**
   * Creates a new set of products.
   *
   * @param productDto
   * @return Flux<ProductDto>
   */
  @PostMapping(
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_STREAM_JSON_VALUE
  )
  public ResponseEntity<Flux<ProductDto>> createProducts(
    @RequestBody Flux<ProductDto> productDto,
    @AuthenticationPrincipal Principal principal
  ) {
    log.debug("createProduct");

    return new ResponseEntity<>(
      productService.createProduct(productDto, principal.getName()),
      HttpStatus.CREATED
    );
  }

  /**
   * Updates the quantity of existing products as per files matching them by article and size.
   *
   * @param files Flux<FilePart>
   * @return <Flux<FileUploadResponseDto>
   */
  @PatchMapping(
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
    produces = MediaType.APPLICATION_STREAM_JSON_VALUE
  )
  public ResponseEntity<Flux<FileUploadDto>> patchProductQuantity(
    @RequestPart("file") Flux<FilePart> files,
    @AuthenticationPrincipal Principal principal
  ) {
    log.debug("shouldPatchProductQuantity");
    return ResponseEntity.accepted().body(
      uploadProductService.patchProductQuantity(files, principal.getName())
    );
  }
}
