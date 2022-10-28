LAB := 1
ID := 200110619
NAME := 梁鑫嵘
RAR_NAME := $(ID)-$(NAME)-实验$(LAB).rar
ZIP_NAME := submit.zip
PDF_NAME := $(ID)-$(NAME)-编译原理实验报告.pdf

simple: clean
	zip -r $(ZIP_NAME) .

all: clean
	$(MAKE) rar
	cp docs/lab$(LAB)/*.pdf .
	mv `ls *.pdf` $(PDF_NAME)
	$(MAKE) zip
	$(MAKE) rar-clean
	$(MAKE) pdf-clean

rar-clean:
	rm -rf *.rar

zip-clean:
	rm -rf *.zip

zip:
	zip $(ZIP_NAME) $(RAR_NAME) $(PDF_NAME)

rar: rar-clean
	rar a $(RAR_NAME) . .* *

pdf-clean:
	rm -rf *.pdf

rars.jar:
	curl https://foruda.gitee.com/attach_file/1662968474799709016/rars.jar?token=e0daded732fbbbd056ad5849a579dffa&ts=1666973056&attname=rars.jar -O rars.jar

rars:
	java -jar rars.jar mc CompactDataAtZero a0 nc dec ae255 data/out/assembly_language.asm

clean: zip-clean rar-clean pdf-clean

.PHONY: rar rar-clean clean pdf-clean zip zip-clean all