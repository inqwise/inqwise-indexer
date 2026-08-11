package com.inqwise.indexer.service.admin;

@FunctionalInterface
public interface AdminNodeStatusSource {
	AdminNodeStatusResult status();
}
