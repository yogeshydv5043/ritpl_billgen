package com.ritpl.billgen.web.form;

import com.ritpl.billgen.entity.ProductCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CategoryForm {
    @NotBlank @Size(max=120) private String name;
    @Size(max=300) private String description;

    public static CategoryForm from(ProductCategory category) {
        CategoryForm form = new CategoryForm();
        form.name = category.getName();
        form.description = category.getDescription();
        return form;
    }

    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
}